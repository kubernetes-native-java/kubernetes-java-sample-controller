/*
 * Copyright 2019-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kubernetes.client.examples;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class DateTimeTests {

	@Test
	void testConversion() throws Exception {
		DateTime joda = DateTime.now(DateTimeZone.forID("US/Pacific"));
		OffsetDateTime offset = OffsetDateTime.ofInstant(Instant.ofEpochMilli(joda.getMillis()),
				ZoneId.of(joda.getZone().getID()));
		DateTime end = new DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.forID(offset.getOffset().getId()));
		assertThat(joda.toString()).isEqualTo(offset.toString());
		assertThat(offset.toString()).isEqualTo(end.toString());
	}

	@Test
	void testZOffset() throws Exception {
		OffsetDateTime offset = OffsetDateTime.parse("2020-01-04T04:34:56Z");
		DateTime end = new DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.forID("UTC"));
		assertThat(offset.toString()).isEqualTo(end.toString().replace(".000", ""));
	}

	@Test
	void testZOffsetMicroOnly() throws Exception {
		OffsetDateTime offset = OffsetDateTime.parse("2020-01-04T04:34:56.001Z");
		DateTime end = new DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.forID("UTC"));
		assertThat(offset.toString()).isEqualTo(end.toString());
	}

	@Test
	void testZOffsetMicro() throws Exception {
		OffsetDateTime offset = OffsetDateTime.parse("2020-01-04T04:34:56.001000Z");
		DateTime end = new DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.forID("UTC"));
		assertThat(offset.toString()).isEqualTo(end.toString());
	}

	@Test
	void testZOffsetNano() throws Exception {
		OffsetDateTime offset = OffsetDateTime.parse("2020-01-04T04:34:56.001002Z");
		DateTime end = new DateTime(offset.toInstant().toEpochMilli(), DateTimeZone.forID("UTC"));
		assertThat(offset.toString()).isEqualTo("2020-01-04T04:34:56.001002Z");
		assertThat(offset.toString().replace("002", "")).isEqualTo(end.toString());
		OffsetDateTime other = OffsetDateTime.ofInstant(Instant.ofEpochMilli(end.getMillis()), ZoneId.of("Z"));
		// You lose the nanoseconds when converting from JDK to Joda
		assertThat(other.toString()).isEqualTo(end.toString());
	}

}
