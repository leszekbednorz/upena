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
package com.jivesoftware.os.upena.ui;

import com.jivesoftware.os.upena.shared.Key;
import com.jivesoftware.os.upena.shared.KeyValueFilter;
import com.jivesoftware.os.upena.shared.Stored;
import com.jivesoftware.os.upena.shared.TimestampedValue;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

public interface JObjectFields<K extends Key, V extends Stored, F extends KeyValueFilter<K, V>> {

    V fieldsToObject();

    Map<String, JField> objectFields();

    K key();

    K key(String key);

    F fieldsToFilter();

    void update(K key, V v);

    void updateFilter(K key, V v);

    Class<K> keyClass();

    Class<V> valueClass();

    Class<F> filterClass();

    Class<? extends ConcurrentSkipListMap<String, TimestampedValue<V>>> responseClass();

    String shortName(V v);

    JObjectFields<K, V, F> copy();
}