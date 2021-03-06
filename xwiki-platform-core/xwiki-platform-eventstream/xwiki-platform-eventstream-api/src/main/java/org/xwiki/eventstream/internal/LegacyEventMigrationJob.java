/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.eventstream.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.eventstream.Event;
import org.xwiki.eventstream.EventStore;
import org.xwiki.eventstream.EventStream;
import org.xwiki.eventstream.EventStreamException;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.DefaultJobStatus;
import org.xwiki.job.Request;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

/**
 * Copy legacy events to the new event store.
 * 
 * @version $Id$
 * @since 12.6
 */
@Component
@Named(LegacyEventMigrationJob.JOBTYPE)
public class LegacyEventMigrationJob
    extends AbstractJob<LegacyEventMigrationRequest, DefaultJobStatus<LegacyEventMigrationRequest>>
{
    /**
     * The id of the job.
     */
    public static final String JOBTYPE = "eventstream.legacycopy";

    @Inject
    private EventStreamConfiguration configuration;

    @Inject
    private EventStream eventStream;

    @Inject
    private EventStore eventStore;

    @Inject
    private QueryManager queryManager;

    @Override
    protected LegacyEventMigrationRequest castRequest(Request request)
    {
        LegacyEventMigrationRequest indexerRequest;
        if (request instanceof LegacyEventMigrationRequest) {
            indexerRequest = (LegacyEventMigrationRequest) request;
        } else {
            indexerRequest = new LegacyEventMigrationRequest(request);
        }

        return indexerRequest;
    }

    @Override
    public String getType()
    {
        return JOBTYPE;
    }

    @Override
    protected void runInternal() throws Exception
    {
        if (!this.configuration.isEventStoreEnabled() || StringUtils.isEmpty(this.configuration.getEventStore())) {
            this.logger.warn("New event store system is disabled");

            return;
        }

        try {
            this.eventStore = this.componentManager.getInstance(EventStore.class, this.configuration.getEventStore());
        } catch (ComponentLookupException e) {
            this.logger.error("Failed to get the configured store", e);

            return;
        }

        Query query = prepareQuery();

        List<Event> events;
        int offset = 0;
        do {
            events = this.eventStream.searchEvents(query);

            if (getRequest().isVerbose()) {
                this.logger.info("Synchronizing legacy events from index {} to {}", offset, offset + events.size());
            }

            if (!events.isEmpty()) {
                // Filter already existing events
                List<Event> eventsToSave = getEventsToSave(events);

                // Save events
                for (Iterator<Event> it = eventsToSave.iterator(); it.hasNext();) {
                    Event event = it.next();

                    CompletableFuture<Event> future = this.eventStore.saveEvent(event);
                    if (!it.hasNext()) {
                        // Wait until the last event of the batch is saved
                        future.get();
                    }
                }

                if (getRequest().isVerbose()) {
                    this.logger.info("{} events were saved in the new store because they did not already existed",
                        eventsToSave.size());
                }

                // Update the offset
                offset += 100;
                query.setOffset(offset);
            }
        } while (events.size() == 100);
    }

    private Query prepareQuery() throws QueryException
    {
        String queryString;
        if (getRequest().getSince() != null) {
            queryString = "WHERE event.date >= :since";
        } else {
            queryString = "";
        }
        Query query = this.queryManager.createQuery(queryString, Query.HQL);
        query.setLimit(100);
        if (getRequest().getSince() != null) {
            query.bindValue("since", getRequest().getSince());
        }

        return query;
    }

    private List<Event> getEventsToSave(List<Event> events) throws EventStreamException
    {
        // TODO: find out what's wrong with the IN clause (it return less results than it should right now)
        // EventSearchResult existingEvents = this.eventStore.search(
        // new SimpleEventQuery().in(Event.FIELD_ID, events.stream().
        // map(Event::getId).collect(Collectors.toList())),
        // Collections.singleton(Event.FIELD_ID));
        // Set<String> existingIds = existingEvents.stream().map(Event::getId).collect(Collectors.toSet());

        List<Event> eventsToSave = new ArrayList<>(events.size());
        for (Event event : events) {
            // TODO: optimize this a bit but there seems to be a problem with the IN clause, see previous commented code
            if (!this.eventStore.getEvent(event.getId()).isPresent()) {
                eventsToSave.add(event);
            }
        }

        return eventsToSave;
    }
}
