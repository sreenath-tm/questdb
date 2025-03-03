/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.functions.date;

import io.questdb.griffin.AbstractGriffinTest;
import org.junit.Test;

public class MinuteFunctionFactoryTest extends AbstractGriffinTest {
    @Test
    public void testNull() throws Exception {
        assertQuery(
                "minute\n" +
                        "NaN\n",
                "select minute(null)",
                null,
                null,
                true,
                true,
                true
        );
    }

    @Test
    public void testPreEpoch() throws Exception {
        assertQuery(
                "minute\n" +
                        "11\n",
                "select minute('1901-07-11T22:11:21.555998Z'::timestamp)",
                null,
                null,
                true,
                true,
                true
        );
    }

    @Test
    public void testVanilla() throws Exception {
        assertQuery(
                "minute\n" +
                        "8\n",
                "select minute('1997-04-11T22:08:30.555555Z'::timestamp)",
                null,
                null,
                true,
                true,
                true
        );
    }
}
