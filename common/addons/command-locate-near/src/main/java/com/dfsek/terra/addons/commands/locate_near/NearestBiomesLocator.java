package com.dfsek.terra.addons.commands.locate_near;

import com.dfsek.terra.api.world.biome.Biome;
import com.dfsek.terra.api.world.biome.generation.BiomeProvider;
import com.dfsek.terra.api.world.info.WorldProperties;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class NearestBiomesLocator {

    private static final String FORMAT = "Biome: %s, at: X: %d, Y: ~, Z: %d";

    /** Só paraleliza um anel se ele tiver pelo menos este número de chamadas ao provider. */
    private static final int PARALLEL_THRESHOLD = 256;

    /** Células de 2^CELL_SHIFT blocos. 2 = 4x4 (resolução de bioma do Minecraft). 0 = exato. */
    private static final int CELL_SHIFT = 2;

    /** Limite de entradas do cache por provider (~60 bytes cada). Ajuste conforme a RAM. */
    private static final int MAX_CACHE_ENTRIES = 200_000;

    private static final ForkJoinPool POOL =
        new ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));

    private static final Map<BiomeProvider, BiomeCache> CACHES =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Versão assíncrona: use esta a partir do comando para não travar a thread do servidor. */
    public static CompletableFuture<List<String>> getNearestBiomesListAsync(
        @NotNull BiomeProvider provider,
        @NotNull WorldProperties properties,
        int originX, int originZ,
        int radius, int step,
        boolean search3D, int seaLevel
    ) {
        return CompletableFuture.supplyAsync(
            () -> getNearestBiomesList(provider, properties, originX, originZ, radius, step, search3D, seaLevel),
            POOL);
    }

    public static List<String> getNearestBiomesList(
        @NotNull BiomeProvider provider,
        @NotNull WorldProperties properties,
        int originX, int originZ,
        int radius, int step,
        boolean search3D, int seaLevel
    ) {
        step = Math.max(1, step);

        Set<Biome> possible = new HashSet<>();
        provider.getBiomes().forEach(possible::add);

        long seed = properties.getSeed();
        int[] ys = buildSampleYs(search3D, seaLevel, properties.getMinHeight(), properties.getMaxHeight(), step);
        BiomeCache cache = cacheFor(provider, seed);

        Search search = new Search(provider, cache, ys, possible.size());

        // Coluna central
        search.scan(new int[]{ originX }, new int[]{ originZ }, 1);

        // Anéis
        for (int r = step; r <= radius && !search.done(); r += step) {
            int nNS = Math.floorDiv(2 * r, step) + 1;                       // norte/sul, com cantos
            int nEW = Math.max(0, Math.floorDiv(2 * r - 2 * step, step) + 1); // leste/oeste, sem cantos
            int n = 2 * nNS + 2 * nEW;

            int[] xs = new int[n];
            int[] zs = new int[n];
            int i = 0;
            for (int k = 0; k < nNS; k++) {
                int d = -r + k * step;
                xs[i] = originX + d; zs[i++] = originZ - r;
                xs[i] = originX + d; zs[i++] = originZ + r;
            }
            for (int k = 0; k < nEW; k++) {
                int d = -r + step + k * step;
                xs[i] = originX + r; zs[i++] = originZ + d;
                xs[i] = originX - r; zs[i++] = originZ + d;
            }
            search.scan(xs, zs, n);
        }
        return search.format();
    }

    // ---------------------------------------------------------------------------------------

    private static BiomeCache cacheFor(BiomeProvider provider, long seed) {
        synchronized (CACHES) {
            BiomeCache c = CACHES.get(provider);
            if (c == null || c.seed != seed) {
                c = new BiomeCache(seed);
                CACHES.put(provider, c);
            }
            return c;
        }
    }

    /** Y do nível do mar primeiro; em 3D, sobe/desce a partir dele em passos maiores. */
    private static int[] buildSampleYs(boolean search3D, int seaLevel, int minY, int maxY, int step) {
        int sea = Math.clamp(seaLevel, minY, maxY - 1);
        if (!search3D) {
            return new int[]{ sea };
        }
        int yStep = Math.max(step, 16);
        List<Integer> list = new ArrayList<>();
        list.add(sea);
        for (int o = yStep; sea + o < maxY || sea - o >= minY; o += yStep) {
            if (sea + o < maxY) list.add(sea + o);
            if (sea - o >= minY) list.add(sea - o);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private record Found(Biome biome, int x, int z) { }

    /** Cache thread-safe (x, y, z) -> Biome, com chave empacotada em long. */
    private static final class BiomeCache {
        final long seed;
        private final ConcurrentHashMap<Long, Biome> map = new ConcurrentHashMap<>();

        BiomeCache(long seed) {
            this.seed = seed;
        }

        Biome get(BiomeProvider provider, int x, int y, int z) {
            int cx = x >> CELL_SHIFT;
            int cz = z >> CELL_SHIFT;
            // 26 bits para x, 26 bits para z, 12 bits para y (deslocado para ficar positivo)
            long key = ((long) cx & 0x3FFFFFFL) << 38
                       | ((long) cz & 0x3FFFFFFL) << 12
                       | ((y + 2048) & 0xFFF);

            Biome b = map.get(key);
            if (b == null) {
                // Amostra sempre no canto da célula: resultado determinístico, independe de quem chegou primeiro.
                b = provider.getBiome(cx << CELL_SHIFT, y, cz << CELL_SHIFT, seed);
                if (map.size() >= MAX_CACHE_ENTRIES) {
                    map.clear(); // política simples; troque por Caffeine se quiser LRU
                }
                map.put(key, b);
            }
            return b;
        }
    }

    private static final class Search {
        private final BiomeProvider provider;
        private final BiomeCache cache;
        private final int[] ys;
        private final int total;
        private final Set<Biome> seen = new HashSet<>();
        private final List<Found> found = new ArrayList<>();

        Search(BiomeProvider provider, BiomeCache cache, int[] ys, int total) {
            this.provider = provider;
            this.cache = cache;
            this.ys = ys;
            this.total = total;
        }

        boolean done() {
            return seen.size() >= total;
        }

        /** Resolve as n posições (em paralelo se valer a pena) e mescla em ordem. */
        void scan(int[] xs, int[] zs, int n) {
            int yCount = ys.length;
            Biome[] res = new Biome[n * yCount];

            IntConsumer task = p -> {
                for (int j = 0; j < yCount; j++) {
                    res[p * yCount + j] = cache.get(provider, xs[p], ys[j], zs[p]);
                }
            };

            if ((long) n * yCount >= PARALLEL_THRESHOLD) {
                POOL.submit(() -> IntStream.range(0, n).parallel().forEach(task)).join();
            } else {
                for (int p = 0; p < n; p++) task.accept(p);
            }

            // Merge sequencial: preserva a ordem de descoberta (mais perto primeiro)
            for (int p = 0; p < n; p++) {
                for (int j = 0; j < yCount; j++) {
                    Biome b = res[p * yCount + j];
                    if (seen.add(b)) {
                        found.add(new Found(b, xs[p], zs[p]));
                        if (done()) return;
                    }
                }
            }
        }

        List<String> format() {
            List<String> out = new ArrayList<>(found.size());
            for (Found f : found) {
                out.add(String.format(FORMAT, f.biome().getID(), f.x(), f.z()));
            }
            return out;
        }
    }
}