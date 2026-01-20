/*
 * Copyright 2012 Jonathan Leahey
 * 
 * This file is part of Minicraft
 * 
 * Minicraft is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Minicraft is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Minicraft. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.util;

public class Template implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

	public int outCount;
	public Int2 position = new Int2(0, 0);

	private String[][] matrix;

	public boolean shapeless;

	public Template(String[][] matrix, int outCount, boolean shapeless) {
		this.matrix = matrix;
		this.outCount = outCount;
		this.shapeless = shapeless;
	}

	public Template(String[][] matrix, int outCount) {
		this(matrix, outCount, false);
	}

	public boolean compare(String[][] input) {
		if (matrix == null) {
			return false;
		}

		// SHAPELESS CRAFTING LOGIC
		if (shapeless) {
			java.util.List<String> requiredIngredients = new java.util.ArrayList<>();
			// Collect all non-empty ingredients from the recipe
			for (String[] row : matrix) {
				for (String item : row) {
					if (item != null && !item.equals("0") && !item.isEmpty()) {
						requiredIngredients.add(item);
					}
				}
			}

			// Collect all non-empty items from the input grid
			java.util.List<String> inputItems = new java.util.ArrayList<>();
			for (String[] row : input) {
				for (String item : row) {
					if (item != null && !item.equals("0") && !item.isEmpty()) {
						inputItems.add(item);
					}
				}
			}

			// If counts don't match, it's not a match
			if (requiredIngredients.size() != inputItems.size()) {
				return false;
			}

			// Copy list to avoid modifying original
			java.util.List<String> remainingInput = new java.util.ArrayList<>(inputItems);

			// Match each required ingredient against input
			for (String req : requiredIngredients) {
				boolean found = false;
				for (int i = 0; i < remainingInput.size(); i++) {
					if (remainingInput.get(i).equals(req)) {
						remainingInput.remove(i);
						found = true;
						break;
					}
				}
				if (!found) {
					return false; // Ingredient missing
				}
			}

			// If we matched everything, remainingInput should be empty (checked by size
			// check above)
			return true;
		}

		// STANDARD SHAPED CRAFTING LOGIC

		// Safety check: input must be at least as big as the template
		if (input.length < matrix.length || input[0].length < matrix[0].length) {
			return false;
		}

		for (int x = 0; x <= (input.length - matrix.length); x++) {
			for (int y = 0; y <= (input[0].length - matrix[0].length); y++) {
				boolean isGood = false;
				boolean isBad = false;
				// Try the template at this position
				for (int i = 0; i < matrix.length; i++) {
					for (int j = 0; j < matrix[0].length; j++) {
						// Double check bounds to prevent crashes (fixes ArrayIndexOutOfBoundsException)
						if (i >= matrix.length || j >= matrix[i].length ||
								x + i >= input.length || y + j >= input[0].length) {
							isBad = true;
							break;
						}

						String templateItem = matrix[i][j];
						String inputItem = input[x + i][y + j];

						// Template item is empty (0 or null) - can be anything
						if (templateItem == null || templateItem.equals("0") || templateItem.isEmpty()) {
							if (inputItem != null && !inputItem.equals("0") && !inputItem.isEmpty()) {
								// Input has something where template expects empty
								return false;
							}
						}
						// Template requires specific item
						else {
							if (inputItem == null || inputItem.equals("0") || inputItem.isEmpty()) {
								// Input is empty where template requires item
								isBad = true;
							} else if (!templateItem.equals(inputItem)) {
								// Input has wrong item
								return false;
							} else {
								// Match found
								isGood = true;
							}
						}
					}
				}

				if (isGood && !isBad) {
					// Check that all cells outside the recipe pattern are empty
					for (int i = 0; i < input.length; i++) {
						for (int j = 0; j < input[0].length; j++) {
							// Skip cells that are part of the recipe pattern
							if (i >= x && i < x + matrix.length && j >= y && j < y + matrix[0].length) {
								continue;
							}
							// Check if this cell is empty
							String cellItem = input[i][j];
							if (cellItem != null && !cellItem.equals("0") && !cellItem.isEmpty()) {
								// Found non-empty cell outside recipe pattern
								isBad = true;
								break;
							}
						}
						if (isBad)
							break;
					}

					if (!isBad) {
						position.x = x;
						position.y = y;
						return true;
					}
				}
			}
		}

		return false;
	}
}
