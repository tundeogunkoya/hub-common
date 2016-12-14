/*******************************************************************************
 * Copyright (C) 2016 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package com.blackducksoftware.integration.hub.notification.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import com.blackducksoftware.integration.hub.dataservice.notification.item.NotificationContentItem;
import com.blackducksoftware.integration.hub.notification.processor.event.NotificationEvent;

public abstract class NotificationProcessor<T> {

    private final Map<Class<?>, NotificationSubProcessor<?>> processorMap = new HashMap<>();

    private final List<MapProcessorCache<?>> cacheList = new ArrayList<>();

    public T process(final SortedSet<NotificationContentItem> notifications) {
        createEvents(notifications);
        final Collection<NotificationEvent<?>> events = collectEvents();
        return processEvents(events);
    }

    private void createEvents(final SortedSet<NotificationContentItem> notifications) {
        for (final NotificationContentItem item : notifications) {
            final Class<?> key = item.getClass();
            if (processorMap.containsKey(key)) {
                final NotificationSubProcessor<?> processor = processorMap.get(key);
                processor.process(item);
            }
        }
    }

    public abstract T processEvents(Collection<NotificationEvent<?>> eventCollection);

    private Collection<NotificationEvent<?>> collectEvents() {
        final Collection<NotificationEvent<?>> eventList = new LinkedList<>();
        for (final MapProcessorCache<?> processor : cacheList) {
            eventList.addAll(processor.getEvents());
        }
        return eventList;
    }

    public Map<Class<?>, NotificationSubProcessor<?>> getProcessorMap() {
        return processorMap;
    }

    public List<MapProcessorCache<?>> getCacheList() {
        return cacheList;
    }
}
