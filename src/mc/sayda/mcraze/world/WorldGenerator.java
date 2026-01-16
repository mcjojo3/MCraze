/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 * 
 * This file is part of MCraze
 * 
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.world;

import mc.sayda.mcraze.logging.GameLogger;

import java.util.ArrayList;
import java.util.Random;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.util.Int2;

public class WorldGenerator {

	public static boolean[][] visibility;
	public static Int2 playerLocation;
	public static TileID[][] backdrops; // Track backdrops during generation

	/**
	 * Biome-specific terrain generation parameters
	 */
	private static class BiomeParams {
		final int surfaceOffset; // Height adjustment from median
		final double surfaceVariation; // Multiplier for height changes
		final TileID surfaceBlock; // Top layer block
		final TileID subSurfaceBlock; // Subsurface block (2-5 deep)
		final double treeDensity; // Tree spawn probability

		BiomeParams(int surfaceOffset, double surfaceVariation, TileID surfaceBlock,
				TileID subSurfaceBlock, double treeDensity) {
			this.surfaceOffset = surfaceOffset;
			this.surfaceVariation = surfaceVariation;
			this.surfaceBlock = surfaceBlock;
			this.subSurfaceBlock = subSurfaceBlock;
			this.treeDensity = treeDensity;
		}
	}

	/**
	 * Get biome-specific generation parameters
	 */
	private static BiomeParams getBiomeParams(Biome biome) {
		// Sea level is at median height (typically 128)
		// NOTE: Y increases DOWNWARD (Y=0 is sky, Y=256 is bedrock)
		// Offsets are relative to median - NEGATIVE = above water (lower Y), POSITIVE =
		// below water (higher Y)
		switch (biome) {
			case DESERT:
				// Deserts: above sea level (NEGATIVE offset = higher up), very flat, no trees
				return new BiomeParams(-5, 0.3, TileID.SAND, TileID.SAND, 0.0);
			case FOREST:
				// Forests: above sea level (NEGATIVE offset = higher up), varied terrain, many
				// trees
				// Reduced variation from 0.7 to 0.5 and increased offset to -7 to keep above
				// water
				return new BiomeParams(-7, 0.5, TileID.GRASS, TileID.DIRT, 0.35);
			case MOUNTAIN:
				// Mountains: above sea level (NEGATIVE offset = higher up), varied terrain,
				// many trees
				// Reduced variation from 0.7 to 0.5 and increased offset to -7 to keep above
				// water
				return new BiomeParams(-21, 2.0, TileID.STONE, TileID.STONE, 0.0);
			case OCEAN:
				// Oceans: BELOW sea level (POSITIVE offset = deeper down), sandy ocean floor
				// with water above
				return new BiomeParams(8, 0.4, TileID.SAND, TileID.SAND, 0.0);
			case PLAINS:
			default:
				// Plains: above sea level (NEGATIVE offset = higher up), moderate variation, no
				// trees
				// Reduced variation from 0.6 to 0.45 and increased offset to -6 to keep above
				// water
				return new BiomeParams(-6, 0.45, TileID.GRASS, TileID.DIRT, 0.02);
		}
	}

	// ===== PERLIN NOISE IMPLEMENTATION =====

	/**
	 * Smooth fade function for Perlin noise interpolation
	 * Uses 6t^5 - 15t^4 + 10t^3 for smooth curves
	 */
	private static double fade(double t) {
		return t * t * t * (t * (t * 6 - 15) + 10);
	}

	/**
	 * Linear interpolation
	 */
	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	/**
	 * Get deterministic gradient value for a position
	 * Uses hash function for pseudo-random but consistent values
	 */
	private static double getGradient1D(int x, long seed) {
		// Hash function to get deterministic pseudo-random gradient
		long hash = x * 374761393L + seed * 668265263L;
		hash = (hash ^ (hash >> 13)) * 1274126177L;
		hash = hash ^ (hash >> 16);

		// Convert to range [-1, 1]
		return ((hash % 10000) / 5000.0) - 1.0;
	}

	/**
	 * 1D Perlin noise function
	 * Returns value in range roughly [-1, 1]
	 */
	private static double perlinNoise1D(double x, long seed) {
		int x0 = (int) Math.floor(x);
		int x1 = x0 + 1;

		double dx = x - x0;

		// Get gradients at surrounding grid points
		double g0 = getGradient1D(x0, seed);
		double g1 = getGradient1D(x1, seed);

		// Calculate influence of each gradient
		double v0 = g0 * dx;
		double v1 = g1 * (dx - 1);

		// Smoothly interpolate between them
		return lerp(fade(dx), v0, v1);
	}

	/**
	 * Fractional Brownian Motion (fBm) - Multiple octaves of Perlin noise
	 * Creates natural terrain with large features and fine detail
	 *
	 * @param x           Position
	 * @param seed        Random seed
	 * @param octaves     Number of noise layers (more = more detail)
	 * @param persistence How much each octave contributes (0.5 = each octave is
	 *                    half as strong)
	 * @param lacunarity  Frequency multiplier between octaves (2.0 = each octave is
	 *                    twice the frequency)
	 */
	private static double perlinFBM(double x, long seed, int octaves, double persistence, double lacunarity) {
		double total = 0;
		double frequency = 1.0;
		double amplitude = 1.0;
		double maxValue = 0;

		for (int i = 0; i < octaves; i++) {
			total += perlinNoise1D(x * frequency, seed + i * 1000) * amplitude;
			maxValue += amplitude;
			amplitude *= persistence;
			frequency *= lacunarity;
		}

		return total / maxValue; // Normalize to [-1, 1]
	}

	/**
	 * Get blended biome params for smooth transitions (5-block blend zone)
	 */
	private static BiomeParams getBlendedBiomeParams(Biome[] biomeMap, int x, int width) {
		if (biomeMap == null || x < 0 || x >= width) {
			return getBiomeParams(Biome.PLAINS);
		}

		Biome centerBiome = biomeMap[x];
		int transitionRange = 5;
		Biome adjacentBiome = null;
		int distanceFromBoundary = transitionRange + 1;

		// Check for boundary within 5 blocks left or right
		for (int i = 1; i <= transitionRange && adjacentBiome == null; i++) {
			if (x - i >= 0 && biomeMap[x - i] != centerBiome) {
				adjacentBiome = biomeMap[x - i];
				distanceFromBoundary = i;
			} else if (x + i < width && biomeMap[x + i] != centerBiome) {
				adjacentBiome = biomeMap[x + i];
				distanceFromBoundary = i;
			}
		}

		if (adjacentBiome == null) {
			return getBiomeParams(centerBiome);
		}

		// Blend parameters (smooth interpolation using fade curve to prevent spikes)
		double linearFactor = (double) distanceFromBoundary / transitionRange;
		double blendFactor = fade(linearFactor); // Use smooth curve instead of linear
		BiomeParams center = getBiomeParams(centerBiome);
		BiomeParams adjacent = getBiomeParams(adjacentBiome);

		return new BiomeParams(
				(int) Math.round(adjacent.surfaceOffset * (1 - blendFactor) + center.surfaceOffset * blendFactor),
				adjacent.surfaceVariation * (1 - blendFactor) + center.surfaceVariation * blendFactor,
				center.surfaceBlock,
				center.subSurfaceBlock,
				adjacent.treeDensity * (1 - blendFactor) + center.treeDensity * blendFactor);
	}

	/**
	 * Generate biome map for the world with guaranteed biomes
	 * - Ocean biomes at both world edges (like Terraria)
	 * - At least one of each biome type guaranteed
	 * - Biome sizes scale with world size
	 * - Extensible for adding new biomes
	 */
	public static Biome[] generateBiomes(int width, Random random) {
		Biome[] biomes = new Biome[width];

		// Calculate biome sizes based on world width
		int oceanWidth = Math.max(60, width / 12); // Ocean is ~8.3% of world width (min 60 blocks) - REDUCED from
													// width/8
		int minLandBiomeWidth = Math.max(60, width / 16); // Min land biome width scales with world
		int maxLandBiomeWidth = Math.max(150, width / 6); // Max land biome width scales with world

		// Get all non-ocean biomes for guaranteed placement
		Biome[] landBiomes = { Biome.PLAINS, Biome.FOREST, Biome.DESERT, Biome.MOUNTAIN };

		// PHASE 1: Place ocean at left edge
		int leftOceanWidth = oceanWidth + random.nextInt(oceanWidth / 2); // Vary ocean size
		for (int x = 0; x < leftOceanWidth && x < width; x++) {
			biomes[x] = Biome.OCEAN;
		}

		// PHASE 2: Place ocean at right edge
		int rightOceanWidth = oceanWidth + random.nextInt(oceanWidth / 2);
		for (int x = width - rightOceanWidth; x < width; x++) {
			if (x >= 0) {
				biomes[x] = Biome.OCEAN;
			}
		}

		// PHASE 3: Fill middle section with guaranteed + random land biomes
		int middleStart = leftOceanWidth;
		int middleEnd = width - rightOceanWidth;
		int middleWidth = middleEnd - middleStart;

		if (middleWidth > 0) {
			// Create list of biomes to place (guaranteed + random)
			java.util.List<BiomeSegment> segments = new java.util.ArrayList<>();

			// Add guaranteed biomes (one of each land biome)
			// CRITICAL FIX: Scale guaranteed biome widths to fit available space
			int guaranteedCount = landBiomes.length;
			int maxTotalGuaranteedWidth = (int) (middleWidth * 0.8); // Use 80% of space for guaranteed biomes
			int avgGuaranteedWidth = maxTotalGuaranteedWidth / guaranteedCount;

			for (Biome biome : landBiomes) {
				// Vary width but ensure total doesn't exceed available space
				int variance = Math.min(
						random.nextInt(maxLandBiomeWidth - minLandBiomeWidth),
						avgGuaranteedWidth / 2 // Limit variance to half of average
				);
				int segmentWidth = Math.min(
						minLandBiomeWidth + variance,
						avgGuaranteedWidth + avgGuaranteedWidth / 2 // Max = 1.5x average
				);
				segments.add(new BiomeSegment(biome, segmentWidth));
			}

			// Fill remaining space with random biomes
			int usedWidth = segments.stream().mapToInt(s -> s.width).sum();
			while (usedWidth < middleWidth) {
				Biome randomBiome = landBiomes[random.nextInt(landBiomes.length)];
				int segmentWidth = Math.min(
						minLandBiomeWidth + random.nextInt(maxLandBiomeWidth - minLandBiomeWidth),
						middleWidth - usedWidth);
				segments.add(new BiomeSegment(randomBiome, segmentWidth));
				usedWidth += segmentWidth;
			}

			// Shuffle segments for variety (except keep PLAINS near spawn)
			// Keep first segment as PLAINS for good spawn area
			BiomeSegment firstPlains = null;
			for (int i = 0; i < segments.size(); i++) {
				if (segments.get(i).biome == Biome.PLAINS) {
					firstPlains = segments.remove(i);
					break;
				}
			}
			java.util.Collections.shuffle(segments, random);
			if (firstPlains != null) {
				segments.add(0, firstPlains); // Ensure plains at start for spawn
			}

			// Place segments in the middle section
			int currentX = middleStart;
			for (BiomeSegment segment : segments) {
				for (int i = 0; i < segment.width && currentX < middleEnd; i++) {
					biomes[currentX] = segment.biome;
					currentX++;
				}
			}

			// Fill any remaining gaps with PLAINS
			while (currentX < middleEnd) {
				biomes[currentX] = Biome.PLAINS;
				currentX++;
			}
		}

		return biomes;
	}

	/**
	 * Helper class for biome segment placement
	 */
	private static class BiomeSegment {
		Biome biome;
		int width;

		BiomeSegment(Biome biome, int width) {
			this.biome = biome;
			this.width = width;
		}
	}

	/**
	 * Select next biome based on current biome for natural transitions
	 */
	private static Biome selectNextBiome(Biome current, int x, int width, Random random) {
		double chance = random.nextDouble();

		// Define natural biome transitions
		switch (current) {
			case OCEAN:
				// Ocean -> stay Ocean (35%), Plains (40%), Forest (15%), Desert (7%), Mountain
				// (3%)
				if (chance < 0.35)
					return Biome.OCEAN;
				if (chance < 0.75)
					return Biome.PLAINS;
				if (chance < 0.90)
					return Biome.FOREST;
				if (chance < 0.97)
					return Biome.DESERT;
				return Biome.MOUNTAIN;

			case DESERT:
				// Desert -> stay Desert (30%), Plains (45%), Forest (12%), Ocean (8%), Mountain
				// (5%)
				if (chance < 0.30)
					return Biome.DESERT;
				if (chance < 0.75)
					return Biome.PLAINS;
				if (chance < 0.87)
					return Biome.FOREST;
				if (chance < 0.95)
					return Biome.OCEAN;
				return Biome.MOUNTAIN;

			case FOREST:
				// Forest -> stay Forest (30%), Plains (45%), Desert (12%), Ocean (8%), Mountain
				// (5%)
				if (chance < 0.30)
					return Biome.FOREST;
				if (chance < 0.75)
					return Biome.PLAINS;
				if (chance < 0.87)
					return Biome.DESERT;
				if (chance < 0.95)
					return Biome.OCEAN;
				return Biome.MOUNTAIN;

			case MOUNTAIN:
				// Mountain -> Plains (55%), Forest (25%), Desert (10%), Ocean (7%), stay
				// Mountain (3%)
				if (chance < 0.55)
					return Biome.PLAINS;
				if (chance < 0.80)
					return Biome.FOREST;
				if (chance < 0.90)
					return Biome.DESERT;
				if (chance < 0.97)
					return Biome.OCEAN;
				return Biome.MOUNTAIN;

			case PLAINS:
			default:
				// Plains -> stay Plains (40%), Forest (30%), Desert (15%), Ocean (7%), Mountain
				// (8%)
				if (chance < 0.40)
					return Biome.PLAINS;
				if (chance < 0.70)
					return Biome.FOREST;
				if (chance < 0.85)
					return Biome.DESERT;
				if (chance < 0.92)
					return Biome.OCEAN;
				return Biome.MOUNTAIN;
		}
	}

	public static TileID[][] generate(int width, int height, Random random, Biome[] biomeMap, World worldObj) {
		TileID[][] world = new TileID[width][height];
		visibility = new boolean[width][height];
		backdrops = new TileID[width][height]; // Initialize backdrop tracking
		for (int i = 0; i < visibility.length; i++) {
			for (int j = 0; j < visibility[0].length; j++) {
				visibility[i][j] = true;
				world[i][j] = TileID.NONE;
				backdrops[i][j] = null; // No backdrop by default
			}
		}

		playerLocation = new Int2(width / 2, 5);

		int seed = random.nextInt();
		random.setSeed(seed);
		int median = (int) (.5 * height);

		int minDirtDepth = 2;
		int maxDirtDepth = 5;
		int minSurface = (int) (.25 * height);
		int maxSurface = (int) (.75 * height);

		int surface = median;
		int dirtDepth = 3;

		ArrayList<Int2> trees = new ArrayList<Int2>();

		// PHASE 1: Generate surface heights using sine waves + random noise
		int[] surfaceHeights = new int[width];
		int[] dirtDepths = new int[width];

		// Initialize random with seed for reproducible terrain
		Random terrainRandom = new Random(seed);

		for (int i = 0; i < width; i++) {
			// Get biome-specific parameters with transition blending
			BiomeParams biomeParams = getBlendedBiomeParams(biomeMap, i, width);

			// Generate dirt depth with some randomness
			dirtDepth = minDirtDepth + terrainRandom.nextInt(maxDirtDepth - minDirtDepth + 1);

			// Multi-frequency sine wave terrain generation
			// Large features (4 cycles across world) - major hills and valleys
			double wave1 = Math.sin(i * Math.PI * 2 * 4.0 / width) * biomeParams.surfaceVariation * 6;
			// Medium features (12 cycles) - moderate bumps
			double wave2 = Math.sin(i * Math.PI * 2 * 12.0 / width) * biomeParams.surfaceVariation * 3;
			// Small details (30 cycles) - fine texture
			double wave3 = Math.sin(i * Math.PI * 2 * 30.0 / width) * biomeParams.surfaceVariation * 1.5;

			// Add random noise for natural variation
			double randomNoise = (terrainRandom.nextDouble() - 0.5) * biomeParams.surfaceVariation * 2;

			// Combine all layers (total amplitude: ~12.5x surfaceVariation)
			// Add +0.1 bias then round upward (0.5+ rounds up) - keeps terrain slightly
			// higher
			int heightVariation = (int) Math.round(wave1 + wave2 + wave3 + randomNoise + 0.1);

			// Calculate final surface height
			surface = median + biomeParams.surfaceOffset + heightVariation;

			// CRITICAL: Enforce biome-specific sea level rules BEFORE clamping
			Biome currentBiome = (biomeMap != null && i >= 0 && i < biomeMap.length) ? biomeMap[i] : Biome.PLAINS;

			// Deserts must ALWAYS be above sea level (lower Y values)
			if (currentBiome == Biome.DESERT && surface >= median) {
				surface = median - 1;
			}

			// Oceans must ALWAYS be at or below sea level (higher Y values)
			if (currentBiome == Biome.OCEAN && surface < median) {
				surface = median;
			}

			// Clamp to valid range
			surface = Math.max(minSurface, Math.min(maxSurface, surface));

			surfaceHeights[i] = surface;
			dirtDepths[i] = dirtDepth;
		}

		// PHASE 1.5: Smooth ocean-to-land transitions to create beaches/slopes
		// This prevents sharp cliffs between ocean and land biomes
		int transitionWidth = 12; // Width of transition zone in blocks
		for (int i = 0; i < width; i++) {
			Biome currentBiome = (biomeMap != null && i < biomeMap.length) ? biomeMap[i] : Biome.PLAINS;

			// Only process ocean-to-land or land-to-ocean transitions
			if (currentBiome == Biome.OCEAN) {
				// Check if we're near the right edge of ocean (transitioning to land)
				int landX = -1;
				for (int j = 1; j <= transitionWidth && i + j < width; j++) {
					Biome checkBiome = biomeMap[i + j];
					if (checkBiome != Biome.OCEAN) {
						landX = i + j;
						break;
					}
				}

				if (landX != -1) {
					// We found land to the right - create smooth transition
					int oceanHeight = surfaceHeights[i];
					int landHeight = surfaceHeights[landX];
					int distance = landX - i;

					// Interpolate heights across the transition
					for (int j = 1; j < distance; j++) {
						int transitionX = i + j;
						if (transitionX < width) {
							// Linear interpolation with slight bias toward land height
							double progress = (double) j / distance;
							// Use smoothstep for more natural curve (slow-fast-slow)
							progress = progress * progress * (3 - 2 * progress);
							int smoothHeight = (int) Math.round(oceanHeight + (landHeight - oceanHeight) * progress);
							surfaceHeights[transitionX] = smoothHeight;
						}
					}
				}
			} else {
				// Land biome - check if we're near the left edge (transitioning from ocean)
				int oceanX = -1;
				for (int j = 1; j <= transitionWidth && i - j >= 0; j++) {
					Biome checkBiome = biomeMap[i - j];
					if (checkBiome == Biome.OCEAN) {
						oceanX = i - j;
						break;
					}
				}

				if (oceanX != -1) {
					// We found ocean to the left - create smooth transition
					int oceanHeight = surfaceHeights[oceanX];
					int landHeight = surfaceHeights[i];
					int distance = i - oceanX;

					// Interpolate heights across the transition
					for (int j = 1; j < distance; j++) {
						int transitionX = oceanX + j;
						if (transitionX < width && transitionX >= 0) {
							// Linear interpolation with slight bias toward land height
							double progress = (double) j / distance;
							// Use smoothstep for more natural curve (slow-fast-slow)
							progress = progress * progress * (3 - 2 * progress);
							int smoothHeight = (int) Math.round(oceanHeight + (landHeight - oceanHeight) * progress);
							surfaceHeights[transitionX] = smoothHeight;
						}
					}
				}
			}
		}

		// PHASE 2: Place blocks based on Perlin-generated heights
		boolean playerLocFound = false;
		for (int i = 0; i < width; i++) {
			BiomeParams biomeParams = getBlendedBiomeParams(biomeMap, i, width);
			surface = surfaceHeights[i];
			dirtDepth = dirtDepths[i];

			if (random.nextDouble() < biomeParams.treeDensity) {
				trees.add(new Int2(i, surface - 1));
			}

			if (i > width / 4 && surface < median && !playerLocFound) {
				playerLocation.x = i;
				playerLocation.y = surface - 2;
				playerLocFound = true;
			}

			for (int j = 0; j <= surface; j++) {
				setVisible(i + 1, j);
				setVisible(i, j + 1);
				setVisible(i - 1, j);
				setVisible(i, j - 1);
			}

			world[i][surface] = biomeParams.surfaceBlock;
			for (int j = 1; j <= dirtDepth; j++) {
				world[i][surface + j] = biomeParams.subSurfaceBlock;
				visibility[i][surface + j] = false;
			}
			for (int j = dirtDepth; surface + j < height; j++) {
				world[i][surface + j] = TileID.STONE;
				visibility[i][surface + j] = false;
			}
		}

		// water - FIXED: Check if terrain surface is above sea level, not just if block
		// exists at sea level
		for (int i = 0; i < width; i++) {
			// NEW: Skip water filling if terrain surface is above sea level
			// NOTE: Lower Y values = higher altitude (Y=0 is sky, Y=128 is sea level)
			if (surfaceHeights[i] < median) {
				continue; // Terrain is above water (lower Y), don't flood this column
			}

			// flood fill down
			for (int j = median; j < height; j++) {
				// setVisible(i+1,j);
				// setVisible(i,j+1);
				// setVisible(i-1,j);
				// setVisible(i,j-1);

				if (world[i][j] != TileID.NONE) {
					carve(world, i, j - 1, 1 + random.nextDouble() * 2, TileID.SAND, new TileID[] {
							TileID.WATER, TileID.NONE }, false);
					break;
				}
				world[i][j] = TileID.WATER;
			}
		}

		uniformlyAddMinerals(world, TileID.COAL_ORE, Constants.ORE_COAL_FREQUENCY, (int) (height * .4),
				(int) (height * .9), new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER,
						TileID.NONE },
				random);

		// Iron ore: Common, deeper underground (60% to 95%)
		uniformlyAddMinerals(world, TileID.IRON_ORE, Constants.ORE_IRON_FREQUENCY, (int) (height * .6),
				(int) (height * .95),
				new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER, TileID.NONE }, random);

		// Gold ore: Rare, deep underground only (75% to 95%)
		uniformlyAddMinerals(world, TileID.GOLD_ORE, Constants.ORE_GOLD_FREQUENCY, (int) (height * .75),
				(int) (height * .95),
				new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER, TileID.NONE }, random);

		// Diamond ore: Very rare, deepest layers only (90% to 100%)
		uniformlyAddMinerals(world, TileID.DIAMOND_ORE, Constants.ORE_DIAMOND_FREQUENCY, (int) (height * .9), height,
				new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER, TileID.NONE }, random);

		// Lapis ore: Moderate rarity, specific deep layer (70% to 90%)
		uniformlyAddMinerals(world, TileID.LAPIS_ORE, Constants.ORE_LAPIS_FREQUENCY, (int) (height * .7),
				(int) (height * .9),
				new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER, TileID.NONE }, random);

		// Ruby ore: Extremely rare, very deep with small veins (85% to 100%)
		uniformlyAddMinerals(world, TileID.RUBY_ORE, Constants.ORE_RUBY_FREQUENCY, (int) (height * .85), height,
				new TileID[] { TileID.DIRT, TileID.SAND, TileID.WATER, TileID.NONE }, random, 0.5, 1.5);

		TileID[] caveIgnore = new TileID[] { TileID.DIRT, TileID.COAL_ORE, TileID.WATER,
				TileID.GRASS, TileID.SAND, TileID.NONE };
		// caves
		int caveCount = (int) (width / 16 + random.nextDouble() * 3);
		for (int i = 0; i < caveCount; i++) {
			int posX = random.nextInt(width);
			int posY = random.nextInt(height / 8) + height * 7 / 8;
			int caveLength = random.nextInt(width);
			int directionX = -1 + random.nextInt(3);
			int directionY = -1 + random.nextInt(3);
			for (int j = 0; j < caveLength; j++) {
				double chance = random.nextDouble();
				// change direction
				if (chance > .9) {
					directionX = -1 + random.nextInt(3);
					directionY = -1 + random.nextInt(3);
				}
				posX += directionX + -1 + random.nextInt(3);
				posY += directionY + -1 + random.nextInt(3);
				if (posX < 0 || posX >= width || posY <= median || posY >= height) {
					break;
				}
				double caveSize = 1 + random.nextDouble() * .45;
				carve(world, posX, posY, caveSize, TileID.NONE, caveIgnore, false);
			}
		}

		// Generate dungeons underground (somewhat rare but common - ~5-10 per world)
		int dungeonCount = 5 + random.nextInt(6); // 5-10 dungeons
		for (int i = 0; i < dungeonCount; i++) {
			// Find a spot underground near stone
			for (int attempt = 0; attempt < 50; attempt++) {
				int dungeonX = random.nextInt(width);
				int dungeonY = median + random.nextInt(height - median - 10); // Below sea level

				// Check if this spot is next to stone (cave wall)
				boolean nextToStone = false;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dy = -2; dy <= 2; dy++) {
						int checkX = dungeonX + dx;
						int checkY = dungeonY + dy;
						if (checkX >= 0 && checkX < width && checkY >= 0 && checkY < height) {
							if (world[checkX][checkY] == TileID.STONE) {
								nextToStone = true;
								break;
							}
						}
					}
					if (nextToStone)
						break;
				}

				// Check if area is mostly empty (cave)
				if (nextToStone) {
					int emptyCount = 0;
					int totalChecked = 0;
					for (int dx = 0; dx < 7; dx++) {
						for (int dy = 0; dy < 5; dy++) {
							int checkX = dungeonX + dx;
							int checkY = dungeonY + dy;
							if (checkX >= 0 && checkX < width && checkY >= 0 && checkY < height) {
								totalChecked++;
								if (world[checkX][checkY] == TileID.NONE) {
									emptyCount++;
								}
							}
						}
					}

					// If area is at least 60% empty, place dungeon
					if (totalChecked > 0 && emptyCount > (totalChecked * 0.6)) {
						addTemplate(world, TileTemplate.dungeon, new Int2(dungeonX, dungeonY));

						// Randomly replace 15% of cobble with mossy cobble (dungeon is 7 wide x 6 tall)
						for (int dx = 0; dx < 7; dx++) {
							for (int dy = 0; dy < 6; dy++) {
								int x = dungeonX + dx;
								int y = dungeonY + dy;
								if (x >= 0 && x < width && y >= 0 && y < height) {
									if (world[x][y] == TileID.COBBLE && random.nextDouble() < 0.15) {
										world[x][y] = TileID.MOSSY_COBBLE;
									}
									// Also apply to backdrops
									if (backdrops != null && backdrops[x][y] == TileID.COBBLE
											&& random.nextDouble() < 0.15) {
										backdrops[x][y] = TileID.MOSSY_COBBLE;
									}
								}
							}
						}

						// Populate dungeon chests with loot (chests are at template positions [1][4]
						// and [5][4])
						if (worldObj != null) {
							int chest1X = dungeonX + 1;
							int chest1Y = dungeonY + 4;
							int chest2X = dungeonX + 5;
							int chest2Y = dungeonY + 4;

							// Generate loot for both chests using DUNGEON loot table
							java.util.List<mc.sayda.mcraze.item.InventoryItem> loot1 = LootTable.DUNGEON
									.generate(random);
							java.util.List<mc.sayda.mcraze.item.InventoryItem> loot2 = LootTable.DUNGEON
									.generate(random);

							// Fill chest 1
							ChestData chest1 = worldObj.getOrCreateChest(chest1X, chest1Y);
							int slot = 0;
							for (mc.sayda.mcraze.item.InventoryItem item : loot1) {
								if (slot >= 27)
									break; // 10x3 = 27 slots max
								int slotX = slot % 9;
								int slotY = slot / 9;
								chest1.setInventoryItem(slotX, slotY, item);
								slot++;
							}

							// Fill chest 2
							ChestData chest2 = worldObj.getOrCreateChest(chest2X, chest2Y);
							slot = 0;
							for (mc.sayda.mcraze.item.InventoryItem item : loot2) {
								if (slot >= 27)
									break; // 10x3 = 27 slots max
								int slotX = slot % 9;
								int slotY = slot / 9;
								chest2.setInventoryItem(slotX, slotY, item);
								slot++;
							}

							if (GameLogger.get() != null && GameLogger.get().isDebugEnabled()) {
								GameLogger.get().debug("WorldGenerator: Placed dungeon with loot at (" + dungeonX + ", "
										+ dungeonY + ") - " + loot1.size() + " items in chest 1, " + loot2.size()
										+ " items in chest 2");
							}
						} else {
							if (GameLogger.get() != null && GameLogger.get().isDebugEnabled()) {
								GameLogger.get()
										.debug("WorldGenerator: Placed dungeon at (" + dungeonX + ", " + dungeonY
												+ ")");
							}
						}
						break; // Successfully placed, move to next dungeon
					}
				}
			}
		}

		for (Int2 pos : trees) {
			if (world[pos.x][pos.y + 1] == TileID.GRASS) {
				addTemplate(world, TileTemplate.tree, pos);
			}
		}

		// Add flowers and tall grass decorations based on biome
		TileID[] flowers = new TileID[] { TileID.ROSE, TileID.DANDELION }; // Flower array for easy expansion

		for (int i = 0; i < width; i++) {
			Biome biome = biomeMap[i];

			// Find surface level (check for GRASS or SAND to support all biomes)
			int decorSurface = -1;
			for (int j = 0; j < height; j++) {
				if (world[i][j] == TileID.GRASS || world[i][j] == TileID.SAND) {
					decorSurface = j;
					break;
				}
			}

			if (decorSurface > 0 && decorSurface < height - 1 && world[i][decorSurface - 1] == TileID.NONE) {
				double decorChance = random.nextDouble();

				// Plains: flowers (5%) and tall grass (35%)
				if (biome == Biome.PLAINS) {
					if (decorChance < 0.05) {
						// Place random flower
						world[i][decorSurface - 1] = flowers[random.nextInt(flowers.length)];
					} else if (decorChance < 0.40) {
						// Place tall grass (more common)
						world[i][decorSurface - 1] = TileID.TALL_GRASS;
					}
				}
				// Forest: tall grass (15%), rare flowers (2%)
				else if (biome == Biome.FOREST) {
					if (decorChance < 0.02) {
						// Rare flowers in forest
						world[i][decorSurface - 1] = flowers[random.nextInt(flowers.length)];
					} else if (decorChance < 0.17) {
						// Tall grass
						world[i][decorSurface - 1] = TileID.TALL_GRASS;
					}
				}
				// Desert: cactus (20% spawn chance, infinite stacking with 20% per block)
				else if (biome == Biome.DESERT) {
					if (decorChance < 0.2) {
						// Place base cactus block
						int cactusHeight = decorSurface - 1;
						world[i][cactusHeight] = TileID.CACTUS;

						// Stack additional cactus blocks with 20% chance each
						// Theoretically infinite, but exponentially rare (20% → 4% → 0.8% → 0.16%...)
						int stackY = cactusHeight - 1;
						while (stackY > 0 && random.nextDouble() < 0.2) {
							if (world[i][stackY] == TileID.NONE) {
								world[i][stackY] = TileID.CACTUS;
								stackY--;
							} else {
								break; // Can't stack if space is occupied
							}
						}
					}
				}
			}
		}

		return world;
	}

	// Density [0,1]
	private static void uniformlyAddMinerals(TileID[][] world, TileID mineral, float density,
			int minDepth, int maxDepth, TileID[] ignoreTypes, Random random) {
		uniformlyAddMinerals(world, mineral, density, minDepth, maxDepth, ignoreTypes, random, 1.0, 1.6);
	}

	private static void uniformlyAddMinerals(TileID[][] world, TileID mineral, float density,
			int minDepth, int maxDepth, TileID[] ignoreTypes, Random random, double minVeinSize, double maxVeinSize) {
		int missesAllowed = 100;
		int width = world.length;
		int totalHeight = maxDepth - minDepth;
		int desired = (int) (density * width * totalHeight);
		int added = 0;
		int iterations = 0;
		while (added < desired && added - iterations < missesAllowed) {
			int posX = random.nextInt(width);
			int posY = random.nextInt(totalHeight) + minDepth;
			if (world[posX][posY] == TileID.STONE) {
				double mineralSize = minVeinSize + random.nextDouble() * (maxVeinSize - minVeinSize);
				carve(world, posX, posY, mineralSize, mineral, ignoreTypes, false);
				added++;
			}
			iterations++;
		}
	}

	private static void setVisible(int x, int y) {
		if (x < 0 || x >= visibility.length || y < 0 || y >= visibility[0].length) {
			return;
		}
		visibility[x][y] = true;
	}

	private static void carve(TileID[][] world, int x, int y, double distance, TileID type,
			TileID[] ignoreTypes, boolean left) {
		for (int i = -(int) distance; (!left && i <= (int) distance) || (left && i <= 0); i++) {
			int currentX = x + i;
			if (currentX < 0 || currentX >= world.length) {
				continue;
			}
			for (int j = -(int) distance; j <= (int) distance; j++) {
				int currentY = y + j;
				if (currentY < 0 || currentY >= world[0].length) {
					continue;
				}
				boolean ignoreThis = false;
				for (TileID ignore : ignoreTypes) {
					if (world[currentX][currentY] == ignore) {
						ignoreThis = true;
					}
				}
				if (ignoreThis) {
					continue;
				}
				if (Math.sqrt(i * i + j * j) <= distance) {
					// If carving out stone for a cave (type == NONE), set backdrop to what was
					// there
					if (type == TileID.NONE && world[currentX][currentY] == TileID.STONE) {
						backdrops[currentX][currentY] = TileID.STONE;
					}
					world[currentX][currentY] = type;
				}
			}
		}
	}

	private static void addTemplate(TileID[][] world, TileTemplate tileTemplate, Int2 position) {
		// Place foreground tiles
		for (int i = 0; i < tileTemplate.template.length; i++) {
			for (int j = 0; j < tileTemplate.template[0].length; j++) {
				if (tileTemplate.template[i][j] != TileID.NONE
						&& position.x - tileTemplate.spawnY + i >= 0
						&& position.x - tileTemplate.spawnY + i < world.length
						&& position.y - tileTemplate.spawnX + j >= 0
						&& position.y - tileTemplate.spawnX + j < world[0].length) {
					world[position.x - tileTemplate.spawnY + i][position.y - tileTemplate.spawnX
							+ j] = tileTemplate.template[i][j];
				}
			}
		}

		// Place backdrop tiles (if template has backdrops)
		if (tileTemplate.backdropTemplate != null) {
			for (int i = 0; i < tileTemplate.backdropTemplate.length; i++) {
				for (int j = 0; j < tileTemplate.backdropTemplate[0].length; j++) {
					if (tileTemplate.backdropTemplate[i][j] != TileID.NONE
							&& position.x - tileTemplate.spawnY + i >= 0
							&& position.x - tileTemplate.spawnY + i < world.length
							&& position.y - tileTemplate.spawnX + j >= 0
							&& position.y - tileTemplate.spawnX + j < world[0].length) {
						backdrops[position.x - tileTemplate.spawnY + i][position.y - tileTemplate.spawnX
								+ j] = tileTemplate.backdropTemplate[i][j];
					}
				}
			}
		}
	}
}
