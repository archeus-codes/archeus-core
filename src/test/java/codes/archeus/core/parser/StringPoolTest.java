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

import org.junit.jupiter.api.Test;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringPoolTest {

	@Test
	void line_anyPair_formatsBothWordsAsUppercaseHex() {
		var pool = new StringPool();

		assertThat(pool.line(0x94000130, 0xFCFF0300)).isEqualTo("94000130 FCFF0300");
		assertThat(pool.line(0, -1)).isEqualTo("00000000 FFFFFFFF");
		assertThat(pool.line(0xA, 0)).isEqualTo("0000000A 00000000");
	}

	@Test
	void line_samePair_returnsTheSameString() {
		var pool = new StringPool();

		assertThat(pool.line(0x94000130, 0xFCFF0300)).isSameAs(pool.line(0x94000130, 0xFCFF0300));
	}

	@Test
	void line_swappedWords_areDifferentLines() {
		var pool = new StringPool();

		assertThat(pool.line(1, 2)).isEqualTo("00000001 00000002");
		assertThat(pool.line(2, 1)).isEqualTo("00000002 00000001");
	}

	@Test
	void line_manyPairs_stayCorrectAndSharedAcrossGrowth() {
		var pool = new StringPool();
		List<String> first = new ArrayList<>();

		for (int i = 0; i < 50_000; ++i) {
			first.add(pool.line(i * 7919, ~i));
		}

		for (int i = 0; i < 50_000; ++i) {
			String line = pool.line(i * 7919, ~i);
			assertThat(line).isSameAs(first.get(i));
			assertThat(line).isEqualTo("%08X %08X".formatted(i * 7919, ~i));
		}
	}

	@Test
	void word_sameWord_returnsTheSameUppercaseString() {
		var pool = new StringPool();

		assertThat(pool.word(0xDEADBEEF)).isEqualTo("DEADBEEF").isSameAs(pool.word(0xDEADBEEF));
	}

	@Test
	void word_sameKeyAsALine_isNotTheLine() {
		var pool = new StringPool();

		assertThat(pool.line(0, 5)).isEqualTo("00000000 00000005");
		assertThat(pool.word(5)).isEqualTo("00000005");
	}

	@Test
	void lineFromBuffer_readsTheAddressFirst() {
		var buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0x94000130).putInt(0xFCFF0300).flip();
		var pool = new StringPool();

		assertThat(pool.line(buf)).isEqualTo("94000130 FCFF0300").isSameAs(pool.line(0x94000130, 0xFCFF0300));
		assertThat(buf.remaining()).isZero();
	}

	@Test
	void zeroTerminated_repeatedText_returnsTheSameStringAndConsumesTheZero() {
		var buf = utf8Buffer("Max Money\0Max Money\0Max Monkey\0");
		var pool = new StringPool();

		String first = pool.zeroTerminated(buf);
		assertThat(buf.position()).isEqualTo(10);
		String second = pool.zeroTerminated(buf);
		String other = pool.zeroTerminated(buf);

		assertThat(first).isEqualTo("Max Money").isSameAs(second);
		assertThat(other).isEqualTo("Max Monkey");
		assertThat(buf.remaining()).isZero();
	}

	@Test
	void zeroTerminated_multibyteText_decodesAndShares() {
		var buf = utf8Buffer("ポケモン\0ポケモン\0");
		var pool = new StringPool();

		assertThat(pool.zeroTerminated(buf)).isEqualTo("ポケモン").isSameAs(pool.zeroTerminated(buf));
	}

	@Test
	void zeroTerminated_empty_returnsTheEmptyLiteral() {
		var pool = new StringPool();

		assertThat(pool.zeroTerminated(utf8Buffer("\0"))).isSameAs("");
	}

	@Test
	void zeroTerminated_sameTextInAnotherBuffer_returnsTheSameString() {
		var pool = new StringPool();
		String first = pool.zeroTerminated(utf8Buffer("Max Money\0"));

		assertThat(pool.zeroTerminated(utf8Buffer("Max Money\0"))).isSameAs(first);
	}

	@Test
	void zeroTerminated_textAlreadyGiven_returnsThatString() {
		var pool = new StringPool();
		String first = pool.text(new String("Max Money"));

		assertThat(pool.zeroTerminated(utf8Buffer("Max Money\0"))).isSameAs(first);
	}

	@Test
	void zeroTerminated_withoutAZero_throws() {
		var pool = new StringPool();

		assertThatThrownBy(() -> pool.zeroTerminated(utf8Buffer("Max Money")))
				.isInstanceOf(BufferUnderflowException.class);
	}

	@Test
	void decodeZeroTerminated_doesNotShare() {
		var buf = utf8Buffer("Max Money\0Max Money\0");

		String first = StringPool.decodeZeroTerminated(buf);

		assertThat(StringPool.decodeZeroTerminated(buf)).isEqualTo(first).isNotSameAs(first);
	}

	@Test
	void text_equalStrings_returnTheFirstOne() {
		var pool = new StringPool();
		String first = new String("12345678 abcdef00");

		assertThat(pool.text(first)).isSameAs(first);
		assertThat(pool.text(new String("12345678 abcdef00"))).isSameAs(first);
	}

	@Test
	void text_empty_returnsTheEmptyLiteral() {
		var pool = new StringPool();

		assertThat(pool.text(new String())).isSameAs("");
	}

	private static ByteBuffer utf8Buffer(String text) {
		return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
	}
}
