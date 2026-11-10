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
package codes.archeus.core.util;

import java.util.List;

public final class CheatFileConstants {
	/** A master code is always this many 32-bit words: 4 address and value pairs. */
	public static final int MASTER_CODE_WORDS = 8;

	/** The standard master code, written to a {@code usrcheat.dat} for a game with none. */
	public static final List<String> NULL_MASTER_CODE = List.of(
			"00000000", "00000001",
			"00000000", "00000000",
			"00000000", "00000000",
			"00000000", "00000000"
	);

	/**
	 * The title of a top level code holding a game's Action Replay master code, from CodeJunkies. Unlike the flash cart
	 * master code, it can be any number of lines.
	 */
	public static final String ACTION_REPLAY_MASTER_CODE_TITLE = "Master Code";

	/** The title of the folder of DS and DS Lite backlight codes, which stays a game's last folder. */
	public static final String BACKLIGHT_FOLDER_TITLE = "Backlight Codes";

	/** The XML cheat that holds a master code, as {@code master} followed by its values, as R4CCE names it. */
	public static final String XML_MASTER_CHEAT_NAME = "(M)MasterCode";

	/** The same cheat as Datel's Action Replay code lists name it. */
	public static final String ACTION_REPLAY_XML_MASTER_CHEAT_NAME = "(M)";

	private CheatFileConstants() {}
}
