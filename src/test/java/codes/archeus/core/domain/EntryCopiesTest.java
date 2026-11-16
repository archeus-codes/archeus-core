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
package codes.archeus.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EntryCopiesTest {

	@Test
	void withEntries_game_copiesItsDetailsWithTheEntriesGiven() {
		var game = new Game();
		game.setTitle("Mario Kart DS (USA)");
		game.setSerial("AMCE");
		game.setCrc32("12345678");
		game.setFileOffset(512);
		game.setEnabled(true);
		game.setMasterCode(new ArrayList<>(List.of("1234ABCD", "00000001")));
		game.getCodes().add(new Code());

		var code = new Code();
		var folder = new Folder();

		Game copy = game.withEntries(List.of(code), List.of(folder));

		assertThat(copy).isNotSameAs(game);
		assertThat(copy.getTitle()).isEqualTo("Mario Kart DS (USA)");
		assertThat(copy.getSerial()).isEqualTo("AMCE");
		assertThat(copy.getCrc32()).isEqualTo("12345678");
		assertThat(copy.getFileOffset()).isEqualTo(512);
		assertThat(copy.isEnabled()).isTrue();
		assertThat(copy.getMasterCode()).containsExactly("1234ABCD", "00000001").isNotSameAs(game.getMasterCode());
		assertThat(copy.getCodes()).containsExactly(code);
		assertThat(copy.getFolders()).containsExactly(folder);
		assertThat(game.getCodes()).hasSize(1);
	}

	@Test
	void withEntries_nullMasterCode_givesAnEmptyOne() {
		var game = new Game();
		game.setMasterCode(null);

		assertThat(game.withEntries(List.of(), List.of()).getMasterCode()).isEmpty();
	}

	@Test
	void withCodes_folder_copiesItsDetailsWithTheCodesGiven() {
		var folder = new Folder();
		folder.setTitle("Weather");
		folder.setComment("Choose one.");
		folder.setModifier(true);
		folder.setSortOrder(3);
		folder.getCodes().add(new Code());

		var code = new Code();

		Folder copy = folder.withCodes(List.of(code));

		assertThat(copy).isNotSameAs(folder);
		assertThat(copy.getTitle()).isEqualTo("Weather");
		assertThat(copy.getComment()).isEqualTo("Choose one.");
		assertThat(copy.isModifier()).isTrue();
		assertThat(copy.getSortOrder()).isEqualTo(3);
		assertThat(copy.getCodes()).containsExactly(code);
		assertThat(folder.getCodes()).hasSize(1);
	}
}
