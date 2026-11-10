/*
 * Copyright 2021-2026 Deiondre Andrews (Demonic722)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package codes.archeus.core.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import codes.archeus.core.domain.Game;

/**
 * Splits games into the parts a USAY file (EDGE and CycloDS Evolution) can hold: the iEDGE reads at most 4,096 games,
 * the same limit R4CCE has for both formats. Each part is a run of titles by first letter, such as {@code #-L} and
 * {@code M-Z}. Entries starting with {@code !}, such as {@code !Credits}, go in every part. A letter is never split, so
 * there are as few parts as the letters allow.
 */
public final class UsayParts {
	public static final int MAX_GAMES = 4096;

	private static final char OTHER = '#';

	private static final String EVERY_PART_PREFIX = "!";

	private UsayParts() {
	}

	/**
	 * Games of a USAY file, in the order they came.
	 *
	 * @param name the range of first letters it holds, such as {@code #-L}, or one letter, such as {@code M}
	 */
	public record Part(String name, List<Game> games) {
	}

	/**
	 * The games in parts of at most {@link #MAX_GAMES}.
	 */
	public static List<Part> split(List<Game> games) {
		return split(games, MAX_GAMES);
	}

	/**
	 * The games in parts of at most {@code maxGames}, or one part holding them all when they fit.
	 *
	 * @throws IllegalArgumentException if one letter's games don't fit in a part beside the entries every part holds
	 */
	public static List<Part> split(List<Game> games, int maxGames) {
		List<Game> everyPart = games.stream().filter(UsayParts::inEveryPart).toList();
		Map<Character, Integer> byLetter = countByLetter(games);
		int room = maxGames - everyPart.size();
		List<List<Character>> runs = new ArrayList<>();
		List<Character> run = new ArrayList<>();
		int inRun = 0;

		for (Map.Entry<Character, Integer> letter : byLetter.entrySet()) {
			if (letter.getValue() > room) {
				throw new IllegalArgumentException(
						"%,d games start with %s, more than a part of %,d games can hold"
								.formatted(letter.getValue(), letter.getKey(), maxGames)
				);
			}

			if (inRun + letter.getValue() > room) {
				runs.add(run);
				run = new ArrayList<>();
				inRun = 0;
			}

			run.add(letter.getKey());
			inRun += letter.getValue();
		}

		runs.add(run);
		return runs.stream().map(letters -> part(games, letters)).toList();
	}

	private static Part part(List<Game> games, List<Character> letters) {
		List<Game> inPart = games.stream()
				.filter(game -> inEveryPart(game) || letters.contains(letter(game)))
				.toList();

		if (letters.isEmpty()) {
			return new Part("", inPart);
		}

		char first = letters.getFirst();
		char last = letters.getLast();
		return new Part(first == last ? String.valueOf(first) : first + "-" + last, inPart);
	}

	/**
	 * How many games start with each letter, in the order {@code #}, then {@code A} to {@code Z}.
	 */
	private static Map<Character, Integer> countByLetter(List<Game> games) {
		Map<Character, Integer> counts = new LinkedHashMap<>();
		counts.put(OTHER, 0);

		for (char letter = 'A'; letter <= 'Z'; ++letter) {
			counts.put(letter, 0);
		}

		for (Game game : games) {
			if (!inEveryPart(game)) {
				counts.merge(letter(game), 1, Integer::sum);
			}
		}

		counts.values().removeIf(count -> count == 0);
		return counts;
	}

	private static boolean inEveryPart(Game game) {
		return game.getTitle().startsWith(EVERY_PART_PREFIX);
	}

	private static char letter(Game game) {
		char first = game.getTitle().isEmpty() ? OTHER : Character.toUpperCase(game.getTitle().charAt(0));
		return first >= 'A' && first <= 'Z' ? first : OTHER;
	}
}
