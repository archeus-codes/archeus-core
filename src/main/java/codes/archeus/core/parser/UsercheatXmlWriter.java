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

import codes.archeus.core.domain.CheatFile;
import codes.archeus.core.util.CheatFileConstants;
import codes.archeus.core.domain.Code;
import codes.archeus.core.domain.Folder;
import codes.archeus.core.domain.Game;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Serializes a {@link CheatFile} and its games to XML, as a {@code <codelist>} in either {@link XmlFormat}.
 * Lines end in CRLF and each element's text stays on one line, as R4CCE writes it: the Acekard 2 menu and AKAIO
 * crash rebuilding their cheat list from a file with LF line endings.
 */
public class UsercheatXmlWriter implements CheatFileWriter {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
	private static final String LINE_END = "\r\n";
	private static final Pattern LINE_BREAKS = Pattern.compile("[ \t]*[\r\n]+[ \t]*");

	private final XmlFormat format;

	public UsercheatXmlWriter(XmlFormat format) {
		this.format = format;
	}

	public XmlFormat getFormat() {
		return format;
	}

	@Override
	public void write(CheatFile cheatFile, List<Game> games, OutputStream out) throws IOException {
		try {
			XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8");
			xml.writeStartDocument("UTF-8", "1.0");
			xml.writeCharacters(LINE_END);

			writeCodelist(xml, cheatFile, games);
			xml.writeEndDocument();
			xml.flush();
			xml.close();
		} catch (XMLStreamException e) {
			throw new IOException("Failed to write XML cheat file", e);
		}
	}

	private void writeCodelist(XMLStreamWriter xml, CheatFile cheatFile, List<Game> games) throws XMLStreamException {
		xml.writeStartElement("codelist");
		xml.writeCharacters(LINE_END);

		writeElement(xml, 1, "name", cheatFile.getTitle());

		String date = LocalDateTime.now().format(DATE_FORMAT);

		for (Game game : games) {
			writeGame(xml, game, date);
		}

		xml.writeEndElement(); // codelist
		xml.writeCharacters(LINE_END);
	}

	private void writeGame(XMLStreamWriter xml, Game game, String date) throws XMLStreamException {
		indent(xml, 1);
		xml.writeStartElement("game");
		xml.writeCharacters(LINE_END);

		writeElement(xml, 2, "name", game.getTitle());
		writeElement(xml, 2, "gameid", game.getSerial() + " " + game.getCrc32());
		writeElement(xml, 2, "date", date);

		Code actionReplayMasterCode = format == XmlFormat.ACTION_REPLAY ? actionReplayMasterCode(game) : null;

		if (actionReplayMasterCode != null) {
			writeMasterCheat(xml, actionReplayMasterCode.getCodeLines());
		} else if (writesFlashCartMasterCode(game)) {
			writeMasterCheat(xml, flashCartMasterCodeLines(game.getMasterCode()));
		}

		for (Code code : game.getCodes()) {
			if (code != actionReplayMasterCode) {
				writeCheat(xml, code.getTitle(), code.getComment(), code.getCodeLines(), 2);
			}
		}

		for (Folder folder : game.getFolders()) {
			writeFolder(xml, folder);
		}

		indent(xml, 1);
		xml.writeEndElement(); // game
		xml.writeCharacters(LINE_END);
	}

	private void writeFolder(XMLStreamWriter xml, Folder folder) throws XMLStreamException {
		indent(xml, 2);
		xml.writeStartElement("folder");
		xml.writeCharacters(LINE_END);

		writeElement(xml, 3, "name", folder.getTitle());

		if (folder.getComment() != null && !folder.getComment().isBlank()) {
			writeElement(xml, 3, "note", folder.getComment());
		}

		writeElement(xml, 3, "allowedon", folder.isModifier() ? "1" : "0");

		for (Code code : folder.getCodes()) {
			writeCheat(xml, code.getTitle(), code.getComment(), code.getCodeLines(), 3);
		}

		indent(xml, 2);
		xml.writeEndElement(); // folder
		xml.writeCharacters(LINE_END);
	}

	/**
	 * The game's Action Replay master code, a top level "Master Code" code, which the Action Replay XML writes as its
	 * master code cheat; {@code null} when the game has none.
	 */
	private static Code actionReplayMasterCode(Game game) {
		return game.getCodes().stream()
				.filter(code -> CheatFileConstants.ACTION_REPLAY_MASTER_CODE_TITLE.equals(code.getTitle()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Whether the game's flash cart master code goes in the file. The Action Replay XML never carries it, since an
	 * Action Replay uses its own.
	 */
	private boolean writesFlashCartMasterCode(Game game) {
		return format != XmlFormat.ACTION_REPLAY
				&& !game.getMasterCode().isEmpty()
				&& !game.getMasterCode().equals(CheatFileConstants.NULL_MASTER_CODE);
	}

	/**
	 * The flash cart master code as R4CCE writes it: a zero spacer pair before each data pair, then a fixed trailing
	 * pair.
	 */
	private static List<String> flashCartMasterCodeLines(List<String> masterCode) {
		List<String> lines = new ArrayList<>();

		for (int i = 0; i + 1 < masterCode.size(); i += 2) {
			lines.add("00000000 00000000");
			lines.add(masterCode.get(i) + " " + masterCode.get(i + 1));
		}

		lines.add("00000000 00000001");

		return lines;
	}

	/**
	 * Writes the master code: {@code master}, then the lines.
	 */
	private void writeMasterCheat(XMLStreamWriter xml, List<String> lines) throws XMLStreamException {
		indent(xml, 2);
		xml.writeStartElement("cheat");
		xml.writeCharacters(LINE_END);
		writeElement(xml, 3, "name", format == XmlFormat.ACTION_REPLAY
				? CheatFileConstants.ACTION_REPLAY_XML_MASTER_CHEAT_NAME
				: CheatFileConstants.XML_MASTER_CHEAT_NAME);
		writeElement(xml, 3, "codes", "master " + String.join(" ", lines));
		indent(xml, 2);
		xml.writeEndElement(); // cheat
		xml.writeCharacters(LINE_END);
	}

	private void writeCheat(XMLStreamWriter xml, String name, String note, List<String> lines, int depth) throws XMLStreamException {
		indent(xml, depth);
		xml.writeStartElement("cheat");
		xml.writeCharacters(LINE_END);

		writeElement(xml, depth + 1, "name", name);

		if (note != null && !note.isBlank()) {
			writeElement(xml, depth + 1, "note", note);
		}

		writeElement(xml, depth + 1, "codes", String.join(" ", lines));

		indent(xml, depth);
		xml.writeEndElement(); // cheat
		xml.writeCharacters(LINE_END);
	}

	private void writeElement(XMLStreamWriter xml, int depth, String tag, String value) throws XMLStreamException {
		indent(xml, depth);
		xml.writeStartElement(tag);
		xml.writeCharacters(value != null ? oneLine(value) : "");
		xml.writeEndElement();
		xml.writeCharacters(LINE_END);
	}

	private void indent(XMLStreamWriter xml, int depth) throws XMLStreamException {
		xml.writeCharacters("\t".repeat(depth));
	}

	/**
	 * Joins the text's lines with single spaces, keeping each element on one line. Text without line breaks is
	 * returned unchanged.
	 */
	private static String oneLine(String value) {
		String joined = LINE_BREAKS.matcher(value).replaceAll(" ");
		return joined.equals(value) ? value : joined.strip();
	}
}
