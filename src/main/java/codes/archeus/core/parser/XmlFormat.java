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

/**
 * The two XML cheat files {@link UsercheatXmlWriter} writes. Both have a {@code <codelist>} root and the same games,
 * folders and cheats. They differ in the master code cheat, {@code (M)}: the R4CCE file holds the flash cart master
 * code there, and the Action Replay file the game's Action Replay one.
 */
public enum XmlFormat {
	/** The R4CCE layout, read by AKAIO, NitroHax and others. */
	R4CCE,
	/** A code list for the Action Replay Code Manager, as Datel's own default lists are written. */
	ACTION_REPLAY
}
