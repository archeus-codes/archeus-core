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
import java.util.List;

/**
 * A folder of {@link Code}s in a {@link Game}. In a modifier folder, "one-hot" in R4CCE, only one code is on at a
 * time.
 */
public class Folder {
	private String title = "New Folder";
	private String comment = "";
	private boolean modifier = false;
	private int sortOrder = 0;
	private List<Code> codes = new ArrayList<>();

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public boolean isModifier() {
		return modifier;
	}

	public void setModifier(boolean modifier) {
		this.modifier = modifier;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	/**
	 * A copy of the folder holding these codes, with its title, comment, one at a time setting and sort order.
	 */
	public Folder withCodes(List<Code> codes) {
		var copy = new Folder();
		copy.title = title;
		copy.comment = comment;
		copy.modifier = modifier;
		copy.sortOrder = sortOrder;
		copy.codes = new ArrayList<>(codes);
		return copy;
	}

	public List<Code> getCodes() {
		return codes;
	}

	public void setCodes(List<Code> codes) {
		this.codes = codes;
	}
}
