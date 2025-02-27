/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.mailbox;

import bisq.common.proto.persistable.PersistableEnvelope;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode
public class IgnoredMailboxMap implements PersistableEnvelope {
    @Getter
    private final ConcurrentMap<String, Long> dataMap;

    public IgnoredMailboxMap() {
        this.dataMap = new ConcurrentHashMap<>();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private IgnoredMailboxMap(ConcurrentMap<String, Long> ignored) {
        this.dataMap = ignored;
    }

    @Override
    public protobuf.PersistableEnvelope toProtoMessage() {
        return protobuf.PersistableEnvelope.newBuilder()
                .setIgnoredMailboxMap(protobuf.IgnoredMailboxMap.newBuilder().putAllData(dataMap))
                .build();
    }

    public static IgnoredMailboxMap fromProto(protobuf.IgnoredMailboxMap proto) {
        return new IgnoredMailboxMap(new ConcurrentHashMap<>(proto.getDataMap()));
    }

    public boolean containsKey(String uid) {
        return dataMap.containsKey(uid);
    }

    public void put(String uid, long creationTimeStamp) {
        dataMap.put(uid, creationTimeStamp);
    }
}
