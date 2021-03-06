/**
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.unsafe.impl.batchimport.input.csv;

import java.util.Iterator;

import org.neo4j.csv.reader.CharSeeker;
import org.neo4j.function.Function;
import org.neo4j.helpers.collection.NestingIterator;
import org.neo4j.unsafe.impl.batchimport.InputIterator;
import org.neo4j.unsafe.impl.batchimport.input.InputEntity;

/**
 * Able to deserialize one input group. An input group is a list of one or more input files containing
 * its own header. An import can read multiple input groups. Each group is deserialized by
 * {@link InputEntityDeserializer}.
 */
abstract class InputGroupsDeserializer<ENTITY extends InputEntity>
        extends NestingIterator<ENTITY,DataFactory<ENTITY>>
        implements InputIterator<ENTITY>
{
    private final Header.Factory headerFactory;
    private final Configuration config;
    private final IdType idType;
    private InputIterator<ENTITY> currentInput;
    private long previousInputsCollectivePositions;

    InputGroupsDeserializer( Iterator<DataFactory<ENTITY>> dataFactory, Header.Factory headerFactory,
                             Configuration config, IdType idType )
    {
        super( dataFactory );
        this.headerFactory = headerFactory;
        this.config = config;
        this.idType = idType;
    }

    @Override
    protected InputIterator<ENTITY> createNestedIterator( DataFactory<ENTITY> dataFactory )
    {
        closeCurrent();

        // Open the data stream. It's closed by the batch importer when execution is done.
        Data<ENTITY> data = dataFactory.create( config );
        CharSeeker dataStream = data.stream();

        // Read the header, given the data stream. This allows the header factory to be able to
        // parse the header from the data stream directly. Or it can decide to grab the header
        // from somewhere else, it's up to that factory.
        Header dataHeader = headerFactory.create( dataStream, config, idType );

        return currentInput = entityDeserializer( dataStream, dataHeader, data.decorator() );
    }

    private void closeCurrent()
    {
        if ( currentInput != null )
        {
            previousInputsCollectivePositions += currentInput.position();
            currentInput.close();
            currentInput = null;
        }
    }

    protected abstract InputIterator<ENTITY> entityDeserializer( CharSeeker dataStream, Header dataHeader,
            Function<ENTITY,ENTITY> decorator );

    @Override
    public void close()
    {
        closeCurrent();
    }

    @Override
    public long position()
    {
        return previousInputsCollectivePositions + (currentInput != null ? currentInput.position() : 0);
    }
}
