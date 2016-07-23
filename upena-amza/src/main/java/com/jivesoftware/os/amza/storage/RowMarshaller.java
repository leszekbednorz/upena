/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.amza.storage;

import com.jivesoftware.os.amza.shared.RowIndexKey;
import com.jivesoftware.os.amza.shared.RowIndexValue;

public interface RowMarshaller<R> {

    R toRow(long orderId, RowIndexKey key, RowIndexValue value) throws Exception;

    WALRow fromRow(R row) throws Exception;

    public interface WALRow {
        long getTransactionId();
        RowIndexKey getKey();
        RowIndexValue getValue();
    }

    byte[] valueFromRow(R row) throws Exception;
}