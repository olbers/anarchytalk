/*
 * SimplePacketFactory.java
 *
 * Created on May 12, 2007, 3:49 PM
 *************************************************************************
 * Copyright 2008 Paul Smith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ao.protocol.packets.utils;

import ao.protocol.packets.*;
import ao.protocol.packets.toclient.*;
import ao.protocol.packets.toserver.*;
import ao.protocol.packets.bi.*;

/**
 * {@code AOSimplePacketFactory} is an implementation of 
 * {@link ao.protocol.packets.utils.AOPacketFactory} 
 * that parses packets into all known and implemented packet types.
 *
 * @author Paul Smith
 * @see #toPacket(short, byte[])
 * @see ao.protocol.packets.utils.AOPacketFactory
 * @see ao.protocol.packets.AOPacket
 */
public class SimplePacketFactory implements PacketFactory {

    private final String TEXTMDBFILE;
    /** Creates a new instance of AOSimplePacketFactory */

    public SimplePacketFactory() {
        TEXTMDBFILE = null;
    }

    public SimplePacketFactory(String file) {
        TEXTMDBFILE = file;
    }   // end AOSimplePacketFactory()
    
    public Packet toPacket(short type, byte[] data) throws MalformedPacketException {
        switch (type) {
            //Incoming Packets
            case LoginSeedPacket.TYPE:
                return new LoginSeedPacket(data);            // TYPE 0
            case LoginOkPacket.TYPE:
                return new LoginOkPacket();                   // TYPE 5
            case LoginErrorPacket.TYPE:
                return new LoginErrorPacket(data);            // TYPE 6
            case CharacterListPacket.TYPE:
                return new CharacterListPacket(data);              // TYPE 7
            case CharacterUnknownPacket.TYPE:
                return new CharacterUnknownPacket(data);         // TYPE 10
            case SystemMessagePacket.TYPE:
                return new SystemMessagePacket(data, TEXTMDBFILE);            // TYPE 20
            case VicinityMessagePacket.TYPE:
                return new VicinityMessagePacket(data);       // TYPE 34
            case BroadcastMessagePacket.TYPE:
                return new BroadcastMessagePacket(data);   // TYPE 35
            case SimpleSystemMessagePacket.TYPE:
                return new SimpleSystemMessagePacket(data);         // TYPE 36
            case CharacterUpdatePacket.TYPE:
                return new CharacterUpdatePacket(data);            // TYPE 37
            case PrivateChannelJoinPacket.TYPE:
                return new PrivateChannelJoinPacket(data);         // TYPE 52
            case PrivateChannelPartPacket.TYPE:
                return new PrivateChannelPartPacket(data);         // TYPE 53
            case PrivateChannelCharacterJoinPacket.TYPE:
                return new PrivateChannelCharacterJoinPacket(data);         // TYPE 55
            case PrivateChannelCharacterLeavePacket.TYPE:
                return new PrivateChannelCharacterLeavePacket(data);         // TYPE 56
            case ChannelUpdatePacket.TYPE:
                return new ChannelUpdatePacket(data);         // TYPE 60

            //Outgoing Packets
            case LoginRequestPacket.TYPE:
                return new LoginRequestPacket(data);          // TYPE 2
            case LoginSelectPacket.TYPE:
                return new LoginSelectPacket(data);           // TYPE 3
            case ChatCommandPacket.TYPE:
                return new ChatCommandPacket(data);    // TYPE 120

            //Bidirectional Packets
            case CharacterLookupPacket.TYPE:
                return new CharacterLookupPacket(data, Packet.Direction.IN);    // TYPE 21
            case PrivateMessagePacket.TYPE:
                return new PrivateMessagePacket(data, Packet.Direction.IN);   // TYPE 30
            case FriendUpdatePacket.TYPE:
                return new FriendUpdatePacket(data, Packet.Direction.IN);          // TYPE 40
            case FriendRemovePacket.TYPE:
                return new FriendRemovePacket(data, Packet.Direction.IN);          // TYPE 41
            case PrivateChannelInvitePacket.TYPE:
                return new PrivateChannelInvitePacket(data, Packet.Direction.IN);    // TYPE 50
            case PrivateChannelKickPacket.TYPE:
                return new PrivateChannelKickPacket(data, Packet.Direction.IN);      // TYPE 51
            case PrivateChannelMessagePacket.TYPE:
                return new PrivateChannelMessagePacket(data, Packet.Direction.IN);    // TYPE 57
            case ChannelMessagePacket.TYPE:
                return new ChannelMessagePacket(data, Packet.Direction.IN, TEXTMDBFILE);   // TYPE 65
            case PingPacket.TYPE:
                return new PingPacket(data, Packet.Direction.IN);   // TYPE 100
                
            //Unparsed Packets
            default:
                return new UnparsablePacket(type, data, Packet.Direction.IN);
        }   // end switch
    }   // end toPacket()
}   // end class AOSimplePacketFactory
