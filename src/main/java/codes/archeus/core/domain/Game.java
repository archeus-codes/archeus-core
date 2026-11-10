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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A game entry in a {@code usrcheat.dat} file.
 */
public class Game {
	private String title = "New Game";
	private String serial = "ABCD";
	private String crc32 = "12345678";
	private int fileOffset = 0;
	private boolean enabled = false;
	private List<String> masterCode = new ArrayList<>();
	private List<Folder> folders = new ArrayList<>();
	private List<Code> codes = new ArrayList<>();

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSerial() {
		return serial;
	}

	public void setSerial(String serial) {
		this.serial = serial;
	}

	public String getCrc32() {
		return crc32;
	}

	public void setCrc32(String crc32) {
		this.crc32 = crc32;
	}

	public int getFileOffset() {
		return fileOffset;
	}

	public void setFileOffset(int fileOffset) {
		this.fileOffset = fileOffset;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getMasterCode() {
		return masterCode;
	}

	public void setMasterCode(List<String> masterCode) {
		this.masterCode = masterCode;
	}

	public List<Folder> getFolders() {
		return folders;
	}

	public void setFolders(List<Folder> folders) {
		this.folders = folders;
	}

	public List<Code> getCodes() {
		return codes;
	}

	public void setCodes(List<Code> codes) {
		this.codes = codes;
	}

	/**
	 * The top level codes and folders in one list by {@code sortOrder}, interleaved as the file had them. Parsers
	 * number both from one counter.
	 */
	public List<Object> getTopLevelEntriesInOrder() {
		List<Object> merged = new ArrayList<>(codes.size() + folders.size());
		merged.addAll(codes);
		merged.addAll(folders);
		merged.sort(Comparator.comparingInt(
				entry -> entry instanceof Code code ? code.getSortOrder() : ((Folder) entry).getSortOrder()
		));
		return merged;
	}
}
