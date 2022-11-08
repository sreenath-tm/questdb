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

package io.questdb.griffin.engine.groupby;

import io.questdb.cairo.RecordSink;
import io.questdb.cairo.map.Map;
import io.questdb.cairo.map.MapKey;
import io.questdb.cairo.map.MapValue;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.engine.functions.GroupByFunction;
import io.questdb.std.ObjList;

class SampleByFillNoneRecordCursor extends AbstractVirtualRecordSampleByCursor {
    private final RecordSink keyMapSink;
    private final Map map;
    private final RecordCursor mapCursor;
    private boolean isOpen;

    public SampleByFillNoneRecordCursor(
            Map map,
            RecordSink keyMapSink,
            ObjList<GroupByFunction> groupByFunctions,
            GroupByFunctionsUpdater groupByFunctionsUpdater,
            ObjList<Function> recordFunctions,
            int timestampIndex, // index of timestamp column in base cursor
            TimestampSampler timestampSampler,
            Function timezoneNameFunc,
            int timezoneNameFuncPos,
            Function offsetFunc,
            int offsetFuncPos
    ) {
        super(
                recordFunctions,
                timestampIndex,
                timestampSampler,
                groupByFunctions,
                groupByFunctionsUpdater,
                timezoneNameFunc,
                timezoneNameFuncPos,
                offsetFunc,
                offsetFuncPos
        );
        this.map = map;
        this.keyMapSink = keyMapSink;
        this.record.of(map.getRecord());
        this.mapCursor = map.getCursor();
        this.isOpen = true;
    }

    @Override
    public void close() {
        if (isOpen) {
            map.close();
            super.close();
            isOpen = false;
        }
    }

    @Override
    public boolean hasNext() {
        if (mapCursor.hasNext()) {
            return true;
        }

        if (baseRecord == null) {
            return false;

        }
        this.map.clear();

        this.sampleLocalEpoch = this.localEpoch;
        long next = timestampSampler.nextTimestamp(this.localEpoch);

        // looks like we need to populate key map
        // at the start of this loop 'lastTimestamp' will be set to timestamp
        // of first record in base cursor
        do {
            long timestamp = getBaseRecordTimestamp();
            if (timestamp < next) {
                adjustDSTInFlight(timestamp - tzOffset);
                final MapKey key = map.withKey();
                keyMapSink.copy(baseRecord, key);
                MapValue value = key.createValue();
                if (value.isNew()) {
                    groupByFunctionsUpdater.updateNew(value, baseRecord);
                } else {
                    groupByFunctionsUpdater.updateExisting(value, baseRecord);
                }
                circuitBreaker.statefulThrowExceptionIfTripped();
            } else {
                // map value is conditional and only required when clock goes back
                // we override base method for when this happens
                // see: updateValueWhenClockMovesBack()
                timestamp = adjustDST(timestamp, null, next);
                if (timestamp != Long.MIN_VALUE) {
                    nextSamplePeriod(timestamp);
                    return createMapCursor();
                }
            }
        } while (base.hasNext());

        // we ran out of data, make sure hasNext() returns false at the next
        // opportunity, after we stream map that is.
        baseRecord = null;
        return createMapCursor();
    }

    @Override
    public void of(RecordCursor base, SqlExecutionContext executionContext) throws SqlException {
        super.of(base, executionContext);
        if (!isOpen) {
            this.map.reopen();
            this.isOpen = true;
        }
    }

    @Override
    public void toTop() {
        super.toTop();
        if (base.hasNext()) {
            baseRecord = base.getRecord();
            map.clear();
        }
    }

    private boolean createMapCursor() {
        // reset map iterator
        map.getCursor();
        // we do not have any more data, let map take over
        return mapHasNext();
    }

    private boolean mapHasNext() {
        return mapCursor.hasNext();
    }

    @Override
    protected void updateValueWhenClockMovesBack(MapValue value) {
        final MapKey key = map.withKey();
        keyMapSink.copy(baseRecord, key);
        super.updateValueWhenClockMovesBack(key.createValue());
    }
}
