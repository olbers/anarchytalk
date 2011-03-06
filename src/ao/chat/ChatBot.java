/*
 * ChatBot.java
 *
 * Created on September 14, 2010, 3:30 PM
 *************************************************************************
 * Copyright 2010 Kevin Kendall
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
package ao.chat;

import ao.protocol.packets.utils.SimplePacketFactory;
import ao.protocol.*;
import ao.protocol.packets.MessagePacket;
import ao.protocol.packets.Packet;
import ao.protocol.packets.bi.*;
import ao.protocol.packets.toclient.*;
import ao.protocol.packets.toserver.*;
import ao.protocol.packets.utils.PacketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Vector;

import ao.event.EventListenerList;

public class ChatBot implements Bot {

    //Base variables
    private PacketFactory m_packetFactory;
    private Thread m_thread = null;
    private State m_state = State.DISCONNECTED;
    private String m_loginSeed = null;
    private CharacterInfo m_character = null;
    private int m_pingDelay;
    //Connection
    private Socket m_socket = null;
    private DataInputStream m_in = null;
    private DataOutputStream m_out = null;
    private boolean timingout = false;
    //Events
    private final EventListenerList m_listeners = new EventListenerList();
    // used for synchronization
    private final Object m_readLock = new Object();
    private final Object m_writeLock = new Object();
    private final Object m_stateLock = new Object();
    private final Object m_timeoutLock = new Object();
    private Vector<Object[]> lookupQueue = new Vector();
    //Chat related
    private CharacterIDTable chartable = new CharacterIDTable();
    private GroupTable grouptable = new GroupTable();
    //Tell information
    private int lastTellOut = -1;
    private int lastTellIn = -1;
    //Org information
    private String orgName = null;
    private byte[] orgID = null;

    public enum Queue {

        TELL, FADD, FREM, FDEL, INVITE, KICK;
    }   // end enum State

    /** Creates a new instance of AOSimpleBot */
    public ChatBot() {
        this(60000, new SimplePacketFactory());
    }   // end AOSimpleBot

    /** Creates a new instance of AOSimpleBot */
    public ChatBot(int pingDelay) {
        this(pingDelay, new SimplePacketFactory());
    }   // end AOSimpleBot

    /** Creates a new instance of AOSimpleBot */
    public ChatBot(int pingDelay, PacketFactory packetFactory) {
        m_pingDelay = pingDelay;
        m_packetFactory = packetFactory;
    }   // end AOSimpleBot

    public State getState() {
        synchronized (m_stateLock) {
            return m_state;
        }
    }   // end getState()

    public CharacterInfo getCharacter() {
        return m_character;
    }

    public Packet nextPacket() throws IOException {
        synchronized (m_readLock) {
            if (m_state == State.DISCONNECTED) {
                throw new BotStateException(
                        "This bot is not currently connected to a server. It must be connected before packets can be read.",
                        m_state, State.CONNECTED);
            } else {
                try {
                    // Read and parse a packet from the input stream
                    short type = m_in.readShort();
                    short length = m_in.readShort();
                    byte[] data = new byte[length];
                    m_in.readFully(data);

                    Packet packet = m_packetFactory.toPacket(type, data);

                    // DEBUG: display that a packet was recieved
                    System.out.println("IN: " + packet);

                    // Return the packet
                    return packet;
                } catch (SocketTimeoutException ex) {
                    if (m_state != State.DISCONNECTED) {
                        synchronized (m_timeoutLock) {
                            if (!timingout) {
                                timingout = true;
                                // Send a ping
                                sendPacket(new PingPacket("AOChatBot.java", Packet.Direction.OUT));
                                // Read a ping
                                Packet packet = nextPacket();
                                return packet;
                            } else {
                                synchronized (m_stateLock) {
                                    if (m_socket == null || m_socket.isClosed()) {
                                        println("Connection Lost...");
                                        disconnect();
                                        return null;
                                    } else {
                                        println("Connection Lost...");
                                        disconnect();
                                        throw ex;
                                    }   // end else
                                }   // end synchronized
                            }
                        }
                    } else {
                        return null;
                    }
                } catch (SocketException ex) {
                    synchronized (m_stateLock) {
                        if (m_socket == null || m_socket.isClosed()) {
                            println("Connection Lost...");
                            disconnect();
                            return null;
                        } else {
                            println("Connection Lost...");
                            disconnect();
                            throw ex;
                        }   // end else
                    }   // end synchronized
                } catch (EOFException ex) {
                    synchronized (m_stateLock) {
                        if (m_socket == null || m_socket.isClosed()) {
                            println("Connection Lost...");
                            disconnect();
                            return null;
                        } else {
                            println("Connection Lost...");
                            disconnect();
                            throw ex;
                        }   // end else
                    }   // end synchronized
                } // end catch
            }   // end synchronized 
        }   // end else
    }   // end nextPacket()

    public void sendPacket(Packet packet) throws IOException {
        synchronized (m_writeLock) {
            if (m_state == State.DISCONNECTED) {
                throw new BotStateException(
                        "This bot is not currently connected to a server. It must be connected before packets can be sent.",
                        m_state, State.CONNECTED);
            } else {
                try {
                    short type = packet.getType();
                    byte[] data = packet.getData();

                    m_out.writeShort(type);
                    m_out.writeShort(data.length);
                    m_out.write(data, 0, data.length);
                    m_out.flush();

                    // DEBUG: display that a packet was sent
                    System.out.println("OUT: " + packet);
                } catch (SocketException ex) {
                    if (m_socket.isClosed()) {
                        println("Connection Lost...");
                        disconnect();
                        return;
                    } else {
                        throw ex;
                    }   // end else
                }   // end catch
            }   // end else
        }   // end synchronized 
    }   // end sendPacket()

    public void connect(int dimension) throws IOException {
        connect(DimensionAddress.values()[dimension - 1]);
    }   // end connect()

    public void connect(DimensionAddress server) throws IOException {
        connect(server.getURL(), server.getPort());
    }   // end connect()

    public void connect(String server, int port) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.DISCONNECTED) {
                throw new BotStateException(
                        "This bot is already connected to a server. It must be disconnected before you can connect it to a server.",
                        m_state, State.DISCONNECTED);
            } else {
                try {
                    m_socket = new Socket(server, port);
                    m_in = new DataInputStream(m_socket.getInputStream());
                    m_out = new DataOutputStream(m_socket.getOutputStream());

                    m_socket.setSoTimeout(m_pingDelay);

                    m_state = State.CONNECTED;

                    Packet packet = nextPacket();
                    if (packet instanceof LoginSeedPacket) {
                        m_loginSeed = ((LoginSeedPacket) packet).getLoginSeed();
                        println("Connected");
                        fireConnected();
                    } else {
                        println("Failed to connect");
                        disconnect();
                    }   // end else
                    firePacket(packet);
                } catch (IOException ex) {
                    println("Failed to connect");
                    disconnect();
                    throw ex;
                }   // end catch
            }   // end else
        }   // end synchronized
    }   // end connect()

    public void authenticate(String userName, String password) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.CONNECTED) {
                throw new BotStateException(
                        "This bot is either already authenticated or disconnected. "
                        + "It must connected and unauthenticated to be authenticated.",
                        m_state, State.CONNECTED);
            } else {
                try {
                    String key = LoginKeyGenerator.generateLoginKey(m_loginSeed, userName, password);
                    Packet packet = new LoginRequestPacket(
                            LoginKeyGenerator.PROTOCOL_VERSION, userName, key);
                    sendPacket(packet);

                    packet = nextPacket();
                    if (packet instanceof CharacterListPacket) {
                        m_state = State.AUTHENTICATED;
                        println("Authenticated");
                        fireAuthenticated();
                    } else {
                        println("Failed to authenticate");
                        disconnect();
                    }   // end else
                    firePacket(packet);
                } catch (IOException ex) {
                    println("Failed to authenticate");
                    disconnect();
                    throw ex;
                }   // end catch
            }   // end else
        }   // end synchronized
    }   // end authenticate()

    public void login(CharacterInfo character) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.AUTHENTICATED) {
                throw new BotStateException(
                        "This bot is either already logged in or unauthenticated. "
                        + "It must connected, authenticated, and logged out in order to be logged in.",
                        m_state, State.AUTHENTICATED);
            } else {
                try {
                    Packet packet = new LoginSelectPacket(character.getID());
                    sendPacket(packet);

                    packet = nextPacket();
                    if (packet instanceof LoginOkPacket) {
                        m_state = State.LOGGED_IN;
                        m_character = character;

                        // Encourage garbage collection
                        System.runFinalization();
                        System.gc();

                        println("Logged in");
                        fireLoggedIn();
                    } else {
                        println("Failed to log in");
                        disconnect();
                    }   // end else
                    firePacket(packet);
                } catch (IOException ex) {
                    println("Failed to log in");
                    disconnect();
                    throw ex;
                }   // end catch
            }   // end else
        }   // end synchronized
    }   // end login()

    public void disconnect() throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.DISCONNECTED) {
                if (m_socket != null) {
                    if (m_thread != null) {
                        stopThread();//m_thread.stop();
                    }
                    m_socket.close();
                    m_in.close();
                    m_out.close();

                    m_socket = null;
                    m_in = null;
                    m_out = null;
                    timingout = false;
                } else {
                    System.err.println("Socket is null when disconnecting");
                }// end if

                m_loginSeed = null;
                m_character = null;

                m_state = State.DISCONNECTED;

                // Encourage garbage collection
                System.runFinalization();
                System.gc();

                println("Disconnected");
                fireDisconnected();
            }   // end if
        }   // end synchronized
    }   // end disconnect()

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        disconnect();
    }   // end finalize()

    public void start() {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can be started.",
                        m_state, State.LOGGED_IN);
            } else {
                if (m_thread == null || !m_thread.isAlive()) {
                    m_thread = new Thread(this);
                    m_thread.start();
                    println("Started");
                    fireStarted();
                }   // end if
            }   // end else
        }   // end synchronized
    }   // end start()

    private void stopThread() {
        if (m_thread != null) {
            Thread temp = m_thread;
            m_thread = null;
            temp.interrupt();
        }
    }

    public void run() {
        if (m_state != State.LOGGED_IN) {
            throw new BotStateException("This bot is not currently logged in.", getState(), State.LOGGED_IN);
        }
        Packet packet = null;
        // Start listening for packets
        while (getState() == State.LOGGED_IN) {
            try {
                packet = nextPacket();
                if (packet instanceof PingPacket) {
                    PingPacket ping = (PingPacket) packet;
                    synchronized (m_timeoutLock) {
                        if (ping.getDirection() == Packet.Direction.IN) {
                            timingout = false;
                        }
                    }
                } else if (packet instanceof CharacterUpdatePacket) {
                    CharacterUpdatePacket namePacket = (CharacterUpdatePacket) packet;
                    chartable.add(namePacket.getCharacterID(), namePacket.getCharacterName());
                } else if (packet instanceof CharacterLookupPacket) {
                    CharacterLookupPacket lookPacket = (CharacterLookupPacket) packet;
                    chartable.add(lookPacket.getCharacterID(), lookPacket.getCharacterName());
                    searchQueue(chartable.getID(lookPacket.getCharacterName()), lookPacket.getCharacterName());
                    if (lookPacket.getCharacterID() == -1) {
                        println("Character " + lookPacket.getCharacterName() + " does not exist");
                    }
                } else if (packet instanceof ChannelUpdatePacket) {
                    ChannelUpdatePacket updatePacket = (ChannelUpdatePacket) packet;
                    grouptable.add(updatePacket.getGroupID(), updatePacket.getGroupName());
                    if(updatePacket.getGroupID()[0] == 0x03){
                        orgName = updatePacket.getGroupName();
                        orgID = updatePacket.getGroupID();
                    }
                } else if (packet instanceof ChannelMessagePacket) {
                    ChannelMessagePacket msgPacket = (ChannelMessagePacket) packet;
                    fireChannelMessage(msgPacket);
                } else if (packet instanceof PrivateMessagePacket) {
                    PrivateMessagePacket msgPacket = (PrivateMessagePacket) packet;
                    lastTellIn = msgPacket.getCharID();
                    firePrivateMessage(msgPacket);
                } else if (packet instanceof PrivateChannelMessagePacket) {
                    PrivateChannelMessagePacket msgPacket = (PrivateChannelMessagePacket) packet;
                    firePrivateChannelMessage(msgPacket);
                }
                firePacket(packet);
                Thread.sleep(1);
            } catch (InterruptedException ie) {
                fireException(ie);
                return;
            } catch (Exception e) {
                fireException(e);
            }
        }
    }

    /**
     * Actions
     */
    private synchronized void enQueue(Queue type, String name, String msg) {
        Object[] temp = new Object[3];
        temp[0] = type;
        temp[1] = name;
        temp[2] = msg;
        lookupQueue.add(temp);
    }

    private synchronized void searchQueue(int id, String name) throws CharNotFoundException {
        for (int i = 0; i < lookupQueue.size(); i++) {
            Object[] current = lookupQueue.elementAt(i);
            Queue type = (Queue) current[0];
            String lname = (String) current[1];
            String msg = (String) current[2];
            if (id != -1 && lname.compareTo(name) == 0) {//Verify that the name is valid
                try {
                    switch (type) {
                        case TELL:
                            sendTell(lname, msg, false);
                            break;
                        case FADD:
                            addFriend(lname, false);
                            break;
                        case FREM:
                            removeFriend(lname, false);
                            break;
                        case FDEL:
                            deleteFriend(lname, false);
                            break;
                        case INVITE:
                            inviteUser(lname, false);
                            break;
                        case KICK:
                            kickUser(lname, false);
                            break;
                        default:
                            System.out.println("Error: invalid lookup in queue");
                            break;
                    }
                } catch (Exception e) {
                    fireException(e);
                }
                lookupQueue.removeElementAt(i);
                i--;
            } else {
                throw new CharNotFoundException("Character lookup failed, character does not exist", id);
            }
        }
    }

    public void sendChannelMessage(String channel, String msg) throws IOException {
        byte[] id = grouptable.getID(channel);
        if (id != null) {
            sendChannelMessage(id, msg);
        }
    }

    public void sendChannelMessage(byte[] channel, String msg) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else if (msg.compareTo("") != 0) {
                Packet packet = new ChannelMessagePacket(channel, msg);
                sendPacket(packet);
            }
        }
    }

    public void sendPrivateChannelMessage(String channel, String msg) throws IOException {
        if (chartable.getID(channel) != null && chartable.getID(channel) != -1) {
            sendPrivateChannelMessage(chartable.getID(channel), msg);
        }
    }

    public void sendPrivateChannelMessage(int channel, String msg) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else if (msg.compareTo("") != 0) {
                Packet packet = new PrivateChannelMessagePacket(channel, msg);
                sendPacket(packet);
            }
        }
    }

    public void lookup(String name) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can perform a lookup.",
                        m_state, State.LOGGED_IN);
            } else {
                Packet packet = new CharacterLookupPacket(name);
                sendPacket(packet);
            }
        }
    }

    public void sendTell(String name, String msg, boolean lookup) throws IOException {
        int id = -1;
        if (chartable.getID(name) != null) {
            id = chartable.getID(name);
        }
        if (id != -1) {
            sendTell(id, msg);
        } else if (lookup) {
            enQueue(Queue.TELL, name, msg);
            lookup(name);
        }
    }

    public void sendTell(int id, String msg) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else if (id == -1) {
                throw new CharNotFoundException("Could not send tell to character", id);
            } else if (msg.compareTo("") != 0) {
                Packet packet = new PrivateMessagePacket(id, msg, MessagePacket.Direction.OUT);
                sendPacket(packet);
                firePacket(packet);
                lastTellOut = id;
            }
        }
    }

    public void joinChannel(String channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else {
                if (chartable.getID(channel) != null && chartable.getID(channel) != -1) {
                    Packet packet = new PrivateChannelJoinPacket(chartable.getID(channel));
                    sendPacket(packet);
                }
            }
        }
    }

    public void joinChannel(int channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else {
                Packet packet = new PrivateChannelJoinPacket(channel);
                sendPacket(packet);
            }
        }
    }

    public void leaveChannel(String channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else {
                if (chartable.getID(channel) != null && chartable.getID(channel) != -1) {
                    Packet packet = new PrivateChannelPartPacket(chartable.getID(channel));
                    sendPacket(packet);
                }
            }
        }
    }

    public void leaveChannel(int channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send messages.",
                        m_state, State.LOGGED_IN);
            } else {
                Packet packet = new PrivateChannelPartPacket(channel);
                sendPacket(packet);
            }
        }
    }

    public void addFriend(String name, boolean lookup) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before you can add a friend.",
                        m_state, State.LOGGED_IN);
            } else {
                int id = -1;
                if (chartable.getID(name) != null) {
                    id = chartable.getID(name);
                }
                if (id != -1) {
                    Packet packet = new FriendUpdatePacket(id, true);
                    sendPacket(packet);
                } else if (lookup) {
                    Packet packet = new CharacterLookupPacket(name);
                    //Add to friend update queue
                    enQueue(Queue.FADD, name, "");
                    sendPacket(packet);
                }
            }
        }
    }

    public void removeFriend(String name, boolean lookup) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before you can remove a friend.",
                        m_state, State.LOGGED_IN);
            } else {
                int id = -1;
                if (chartable.getID(name) != null) {
                    id = chartable.getID(name);
                }
                if (id != -1) {
                    Packet packet = new FriendUpdatePacket(id, false);
                    sendPacket(packet);
                } else if (lookup) {
                    Packet packet = new CharacterLookupPacket(name);
                    //Add to friend update queue
                    enQueue(Queue.FREM, name, "");
                    sendPacket(packet);
                }
            }
        }
    }

    public void deleteFriend(String name, boolean lookup) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can delete a friend.",
                        m_state, State.LOGGED_IN);
            } else {
                int id = -1;
                if (chartable.getID(name) != null) {
                    id = chartable.getID(name);
                }
                if (id != -1) {
                    Packet packet = new FriendRemovePacket(id);
                    sendPacket(packet);
                } else if (lookup) {
                    Packet packet = new CharacterLookupPacket(name);
                    //Add to friend remove queue
                    enQueue(Queue.FDEL, name, "");
                    sendPacket(packet);
                }
            }
        }
    }

    public void clearFriends() throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before you can clear your temporary friends.",
                        m_state, State.LOGGED_IN);
            } else {
                Packet packet = new ChatCommandPacket("rem buddy ?");
                sendPacket(packet);
            }
        }
    }

    public void inviteUser(String name, boolean lookup) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else {
                int id = -1;
                if (chartable.getID(name) != null) {
                    id = chartable.getID(name);
                }
                if (id != -1) {
                    Packet packet = new PrivateChannelInvitePacket(id);
                    sendPacket(packet);
                    firePacket(packet);
                } else if (lookup) {
                    Packet packet = new CharacterLookupPacket(name);
                    //Add to group invite queue
                    enQueue(Queue.INVITE, name, "");
                    sendPacket(packet);
                }
            }
        }
    }

    public void kickUser(String name, boolean lookup) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else {
                int id = -1;
                if (chartable.getID(name) != null) {
                    id = chartable.getID(name);
                }
                if (id != -1) {
                    Packet packet = new PrivateChannelKickPacket(id);
                    sendPacket(packet);
                    firePacket(packet);
                } else if (lookup) {
                    Packet packet = new CharacterLookupPacket(name);
                    //Add to group kick queue
                    enQueue(Queue.KICK, name, "");
                    sendPacket(packet);
                }
            }
        }
    }

    public void kickAll() throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else {
                Packet packet = new PrivateChannelKickAllPacket();
                sendPacket(packet);
                firePacket(packet);
            }
        }
    }

    public void acceptInvite(String channel) throws IOException {
        if (chartable.getID(channel) != null && chartable.getID(channel) != -1) {
            acceptInvite(chartable.getID(channel));
        }
    }

    public void acceptInvite(int channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else {
                if (channel != -1) {
                    Packet packet = new PrivateChannelJoinPacket(channel);
                    sendPacket(packet);
                }
            }
        }
    }

    public void denyInvite(String channel) throws IOException {
        if (chartable.getID(channel) != null && chartable.getID(channel) != -1) {
            denyInvite(chartable.getID(channel));
        }
    }

    public void denyInvite(int channel) throws IOException {
        synchronized (m_stateLock) {
            if (m_state != State.LOGGED_IN) {
                throw new BotStateException(
                        "This bot is not currently logged in, it must be logged in before it can send tells.",
                        m_state, State.LOGGED_IN);
            } else {
                if (channel != -1) {
                    Packet packet = new PrivateChannelPartPacket(channel);
                    sendPacket(packet);
                }
            }
        }
    }

    public CharacterIDTable getCharTable() {
        return chartable;
    }

    public GroupTable getGroupTable() {
        return grouptable;
    }

    public int getLastTellIn() {
        return lastTellIn;
    }

    public int getLastTellOut() {
        return lastTellOut;
    }

    public String getOrdName(){
        return orgName;
    }

    public byte[] getOrgID(){
        return orgID;
    }

    /**
     * Listeners
     */
    public void addListener(BotListener l) {
        m_listeners.add(BotListener.class, l);
    }   // end addListener

    public void addListener(PacketListener l) {
        m_listeners.add(PacketListener.class, l);
    }   // end addListener

    public void removeListener(BotListener l) {
        m_listeners.remove(BotListener.class, l);
    }   // end removeListener()

    public void removeListener(PacketListener l) {
        m_listeners.remove(PacketListener.class, l);
    }   // end removeListener()

    public void addChannelListener(ChannelListener l) {
        m_listeners.add(ChannelListener.class, l);
    }

    public void removeChannelListener(ChannelListener l) {
        m_listeners.remove(ChannelListener.class, l);
    }

    public void addPrivateMessageListener(PrivateMessageListener l) {
        m_listeners.add(PrivateMessageListener.class, l);
    }

    public void removePrivateMessageListener(PrivateMessageListener l) {
        m_listeners.remove(PrivateMessageListener.class, l);
    }

    public void addPrivateChannelListener(PrivateChannelListener l) {
        m_listeners.add(PrivateChannelListener.class, l);
    }

    public void removePrivateChannelListener(PrivateChannelListener l) {
        m_listeners.remove(PrivateChannelListener.class, l);
    }

    /** 
     * Fires a connected event to all listeners 
     * @see ao.protocol.AOBotListener#connected(ao.protocol.AOBot)
     */
    protected void fireConnected() {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.connected(this);
        }   // end for
    }   // end fireConnected()

    /** 
     * Fires a authenticated event to all listeners 
     * @see ao.protocol.AOBotListener#authenticated(ao.protocol.AOBot)
     */
    protected void fireAuthenticated() {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.authenticated(this);
        }   // end for
    }   // end fireAuthenticated()

    /** 
     * Fires a logged in event to all listeners 
     * @see ao.protocol.AOBotListener#loggedIn(ao.protocol.AOBot)
     */
    protected void fireLoggedIn() {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.loggedIn(this);
        }   // end for
    }   // end fireLoggedIn()

    /** 
     * Fires a started event to all listeners 
     * @see ao.protocol.AOBotListener#started(ao.protocol.AOBot)
     */
    protected void fireStarted() {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.started(this);
        }   // end for
    }   // end fireStarted()

    /** 
     * Fires a disconnected event to all listeners 
     * @see ao.protocol.AOBotListener#disconnected(ao.protocol.AOBot)
     */
    protected void fireDisconnected() {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.disconnected(this);
        }   // end for
    }   // end fireDisconnected()

    protected void firePacket(Packet packet) {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.packet(this, packet);
        }   // end for
        PacketListener[] plisteners = m_listeners.getListeners(PacketListener.class);
        for (PacketListener l : plisteners) {
            l.packet(this, packet);
        }   // end for
    }

    protected void fireException(Exception e) {
        BotListener[] listeners = m_listeners.getListeners(BotListener.class);
        for (BotListener l : listeners) {
            l.exception(this, e);
        }   // end for
    }

    protected void fireChannelMessage(ChannelMessagePacket packet) {
        ChannelListener[] listeners = m_listeners.getListeners(ChannelListener.class);
        for (ChannelListener l : listeners) {
            l.channelMessagePacket(this, packet);
        }   // end for
    }

    protected void firePrivateMessage(PrivateMessagePacket packet) {
        PrivateMessageListener[] listeners = m_listeners.getListeners(PrivateMessageListener.class);
        for (PrivateMessageListener l : listeners) {
            l.privateMessagePacket(this, packet);
        }   // end for
    }

    protected void firePrivateChannelMessage(PrivateChannelMessagePacket packet) {
        PrivateChannelListener[] listeners = m_listeners.getListeners(PrivateChannelListener.class);
        for (PrivateChannelListener l : listeners) {
            l.privateChannelPacket(this, packet);
        }   // end for
    }

    /**
     * Loggers
     */
    public void addLogger(BotLogger l) {
        m_listeners.add(BotLogger.class, l);
    }   // end addListener

    public void removeLogger(BotLogger l) {
        m_listeners.remove(BotLogger.class, l);
    }   // end removeListener()

    /** 
     * Tells all loggers to print a line-terminator
     * @see ao.protocol.AOBotLogger#println(ao.protocol.AOBot)
     */
    protected void println() {
        BotLogger[] listeners = m_listeners.getListeners(BotLogger.class);
        for (BotLogger l : listeners) {
            l.println(this);
        }   // end for
    }   // end println()

    /** 
     * Tells all loggers to print astring followed by a line-terminator
     * @see ao.protocol.AOBotLogger#println(ao.protocol.AOBot, String)
     */
    protected void println(String msg) {
        BotLogger[] listeners = m_listeners.getListeners(BotLogger.class);
        for (BotLogger l : listeners) {
            l.println(this, msg);
        }   // end for
    }   // end println()

    /** 
     * Tells all loggers to print a string
     * @see ao.protocol.AOBotLogger#print(ao.protocol.AOBot, String)
     */
    protected void print(String msg) {
        BotLogger[] listeners = m_listeners.getListeners(BotLogger.class);
        for (BotLogger l : listeners) {
            l.print(this, msg);
        }   // end for
    }   // end println()
}   // end class AOSimpleBot

