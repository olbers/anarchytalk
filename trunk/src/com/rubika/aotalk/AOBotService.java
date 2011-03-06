/*
 * AOBotService.java
 *
 *************************************************************************
 * Copyright 2010 Christofer Engel
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
package com.rubika.aotalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import ao.chat.ChatBot;
import ao.misc.NameFormat;
import ao.protocol.Bot;
import ao.protocol.BotListener;
import ao.protocol.CharacterIDTable;
import ao.protocol.CharacterInfo;
import ao.protocol.DimensionAddress;
import ao.protocol.GroupTable;
import ao.protocol.packets.Packet;
import ao.protocol.packets.bi.ChannelMessagePacket;
import ao.protocol.packets.bi.FriendUpdatePacket;
import ao.protocol.packets.bi.PrivateChannelInvitePacket;
import ao.protocol.packets.bi.PrivateChannelKickPacket;
import ao.protocol.packets.bi.PrivateChannelMessagePacket;
import ao.protocol.packets.bi.PrivateMessagePacket;
import ao.protocol.packets.toclient.BroadcastMessagePacket;
import ao.protocol.packets.toclient.ChannelUpdatePacket;
import ao.protocol.packets.toclient.CharacterListPacket;
import ao.protocol.packets.toclient.LoginErrorPacket;
import ao.protocol.packets.toclient.PrivateChannelCharacterJoinPacket;
import ao.protocol.packets.toclient.PrivateChannelCharacterLeavePacket;
import ao.protocol.packets.toclient.SystemMessagePacket;
import ao.protocol.packets.toclient.VicinityMessagePacket;

public class AOBotService extends Service {
	protected static final String APPTAG = "--> AOTalk::AOBotService";
	private static final String SERVICE_PREFIX = "com.rubika.aotalk.";
	
	private String PASSWORD = "";
	private String USERNAME = "";
	
	public static final String INFO_MESSAGE    = SERVICE_PREFIX + "MESSAGE";
	public static final String INFO_CONNECTION = SERVICE_PREFIX + "SERVER";
	
	public static final String EXTRA_SERVICE    = "status";
	public static final String EXTRA_MESSAGE    = "message";
	public static final String EXTRA_CONNECTION = "server";
	
	public static final String MSG_UPDATE = "update";
	
	public static final String CON_ACCOUNT      = "set account";
	public static final String CON_CONNECTED    = "connected";
	public static final String CON_CHARACTER    = "set character";
	public static final String CON_LFAILURE     = "login failed";
	public static final String CON_STARTED      = "started";
	public static final String CON_SERVER       = "set server";
	public static final String CON_DISCONNECTED = "disconnected";
	public static final String CON_CFAILURE     = "failed to connect";
	public static final String CON_INVITE       = "invited to channel";
	
	public static final String PRIVATE_GROUP_PREFIX = "PG: ";
	
	private Intent newMessageBroadcast;
	private Intent connectionBroadcast;
	
	private List<String> groupList;
	private List<String> groupDisable;
	private List<String> groupIgnore;
	private List<String> watchEnable;
	
	private PrivateChannelInvitePacket invitation = null;
	private CharacterListPacket charpacket 		  = null;
	
	private CharacterInfo aochar;
	private ChatBot aobot;
	private DimensionAddress aoserver;
	
	private List<ChatMessage> messages;
	private List<Friend> onlineFriends;
	private List<Friend> allFriends;
	
	private ChatParser cp;
	private Watch watch;
	
	private boolean afk = false;
	private long afktime;
	
	private NotificationManager noteManager;
	private static final int NOTIFICATION_ID = 1;

	public class ListenBinder extends Binder {
		public AOBotService getService() {
	    	return AOBotService.this;
	    }
	}
	
	/**
	 * Handle the notification
	 * @param message
	 * @param persistent
	 */
	private void setNotification(String message, boolean persistent) {   
	    int icon = R.drawable.icon_notification;    // icon from resources
	    CharSequence tickerText = message;	        // ticker-text
	    long when = System.currentTimeMillis();     // notification time
	    Context context = getApplicationContext();  // application Context
	    CharSequence contentTitle = "AnarchyTalk";	// expanded message title
	    CharSequence contentText = message;    		// expanded message text

	    Intent notificationIntent = new Intent(this, AOTalk.class);
	    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
	    PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

	    Notification notification = new Notification(icon, tickerText, when);
    	notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
	    	    
	    notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
	    noteManager.notify(NOTIFICATION_ID, notification);	
	    
	    //If persistent is false the notification is removed automatically,
	    if(!persistent) {
	    	noteManager.cancel(NOTIFICATION_ID);
	    }
	}
	
	
	/**
	 * Connect to server
	 * Also sets listeners for events and packets
	 */
	public void connect() {	
		if(AOBotService.this.aobot == null) {
	        aobot = new ChatBot();
	        
	        aobot.addListener(new BotListener() {	        	
	        	@Override
				public void authenticated(Bot bot) {
				}

				@Override
				public void connected(Bot bot) {				
					connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_ACCOUNT);
				    getApplicationContext().sendBroadcast(connectionBroadcast);
				}

				@Override
				public void disconnected(Bot bot) {
					//Clear list of available channels
					AOBotService.this.groupList = new ArrayList<String>();
					AOBotService.this.allFriends = new ArrayList<Friend>();
					AOBotService.this.onlineFriends = new ArrayList<Friend>();
					
					connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_DISCONNECTED);
				    getApplicationContext().sendBroadcast(connectionBroadcast);
				    
				    appendToLog(
				    	cp.parse(getString(R.string.disconnected), ChatParser.TYPE_CLIENT_MESSAGE),
				    	null,
				    	null,
				    	ChatParser.TYPE_CLIENT_MESSAGE
				    );
				    
				    if(AOBotService.this.aochar != null) {
				    	setNotification(
				    		AOBotService.this.aochar.getName() + " " + 
				    		getString(R.string.logged_off),
				    		false
				    	);
				    }
				}

				@Override
				public void exception(Bot bot, Exception e) {
					Log.d(APPTAG, "BOT ERROR : " + e.getMessage());					
					e.printStackTrace();
				}

				@Override
				public void loggedIn(Bot bot) {
					connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_CONNECTED);
				    getApplicationContext().sendBroadcast(connectionBroadcast);
				    
				    AOBotService.this.aobot.start();
				    
				    setNotification(
				    	AOBotService.this.aochar.getName() + " " + 
				    	getString(R.string.logged_in),
				    	true
				    );
				}

				@Override
				public void packet(Bot bot, Packet packet) {
					//Character list packet
					if(packet.getType() == CharacterListPacket.TYPE) {
						AOBotService.this.charpacket = (CharacterListPacket) packet;
						
						connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_CHARACTER);
					    getApplicationContext().sendBroadcast(connectionBroadcast);
					}
					
					//Log in failed
					if(packet.getType() == LoginErrorPacket.TYPE) {					
						connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_LFAILURE);
					    getApplicationContext().sendBroadcast(connectionBroadcast);
					}
					
					//Private message
					if(packet.getType() == PrivateMessagePacket.TYPE && packet.getDirection() == Packet.Direction.IN) {
						PrivateMessagePacket privmsg = (PrivateMessagePacket) packet;
						
						if(privmsg != null) {
							String message = privmsg.display(
								AOBotService.this.aobot.getCharTable(), 
								AOBotService.this.aobot.getGroupTable()
							);
							
							//Add AFK sent to the message when user is AFK, and it's not a AFK message..
							if(AOBotService.this.afk && !privmsg.getMessage().contains("is AFK (Away from keyboard)")) {
								message = message + " <font color=#FFFFFF>(AFK reply sent)</font>";
							}
							
							appendToLog(
								cp.parse(message, ChatParser.TYPE_PRIVATE_MESSAGE),
								AOBotService.this.aobot.getCharTable().getName(privmsg.getCharID()),
								null,
								ChatParser.TYPE_PRIVATE_MESSAGE
							);
							
							//Send an AFK message, if the message received is not an AFK message
							if(AOBotService.this.afk && !privmsg.getMessage().contains("is AFK (Away from keyboard)")) {
								//Calculate how long user been AFK
								Date now = new Date();
								long time = (afktime - now.getTime()) / 1000;
								
								String hours   = Integer.toString((int)((time % 3600) / 60));
								String minutes = Integer.toString((int)(time / 3600));
								
								sendTell(
									AOBotService.this.aobot.getCharTable().getName(privmsg.getCharID()),
									NameFormat.format(AOBotService.this.aochar.getName()) + 
									" is currently AFK (Away From Keyboard)" +
									" since " + hours + " hours and " + minutes + " minutes ago.", 
									true,
									false
								);
							}
						}
					}
					
					//Chat group message
					if(packet.getType() == ChannelMessagePacket.TYPE && packet.getDirection() == Packet.Direction.IN) {
						ChannelMessagePacket groupmsg = (ChannelMessagePacket) packet;
						
						//Don't add groups that's in the groupIgnore list 
						if(!AOBotService.this.groupIgnore.contains(aobot.getGroupTable().getName(groupmsg.getGroupID()))) {
							if(!AOBotService.this.groupList.contains(aobot.getGroupTable().getName(groupmsg.getGroupID()))) {
								AOBotService.this.groupList.add(aobot.getGroupTable().getName(groupmsg.getGroupID()));
							}
						}
						
						//Don't show messages from groups that's in the groupDisable list
						if(!AOBotService.this.groupDisable.contains(aobot.getGroupTable().getName(groupmsg.getGroupID()))) {
							if(groupmsg != null) {
								appendToLog(
									/* 
									 * TODO
									 * Need check to see if its OrgMsg, in that case it needs to be handled differently	
									 * Perhaps not important but it would be nice to be able to differentiate between
									 * org and other channels
									 */
									cp.parse(groupmsg.display(
										AOBotService.this.aobot.getCharTable(), 
										AOBotService.this.aobot.getGroupTable()
									), ChatParser.TYPE_GROUP_MESSAGE).replace("] 0:", "]:"),
									AOBotService.this.aobot.getCharTable().getName(groupmsg.getCharID()),
									AOBotService.this.aobot.getGroupTable().getName(groupmsg.getGroupID()),
									ChatParser.TYPE_GROUP_MESSAGE
								);
							}
						}
					}
					
					
					/*
					 * System messages
					 */
					if(packet.getType() == SystemMessagePacket.TYPE && packet.getDirection() == Packet.Direction.IN) {
						SystemMessagePacket syspack = (SystemMessagePacket) packet;
						
						/*
						if(syspack != null) {
							appendToLog(
								cp.parse(syspack.display(), ChatParser.TYPE_SYSTEM_MESSAGE),
								null,
								null,
								ChatParser.TYPE_SYSTEM_MESSAGE
							);
						}
						*/
						
						if(syspack != null) {
							//Received offline message
							if(syspack.getMsgType().equals("a460d92")) {
								appendToLog(
									cp.parse(
										"You got an offline message from " +
										NameFormat.format(AOBotService.this.aobot.getCharTable().getName(syspack.getCharID())),
										ChatParser.TYPE_SYSTEM_MESSAGE
									),
									null,
									null,
									ChatParser.TYPE_SYSTEM_MESSAGE
								);
							}
							
							//Offline message, message buffered
							if(syspack.getMsgType().equals("9740ff4")) {
								appendToLog(
									cp.parse(
										NameFormat.format(AOBotService.this.aobot.getCharTable().getName(syspack.getCharID())) +
										" is offline, message has been buffered",
										ChatParser.TYPE_SYSTEM_MESSAGE
									),
									null,
									null,
									ChatParser.TYPE_SYSTEM_MESSAGE
								);
							}
							
							//Offline message, message buffered
							if(syspack.getMsgType().equals("340e245")) {
								appendToLog(
									cp.parse(
										"Message could not be sent. The recievers inbox is full or the message is too long",
										ChatParser.TYPE_SYSTEM_MESSAGE
									),
									null,
									null,
									ChatParser.TYPE_SYSTEM_MESSAGE
								);
							}
						}
					}
					
					/*
					 * Broadcast Message
					 */
					if(packet.getType() == BroadcastMessagePacket.TYPE && packet.getDirection() == Packet.Direction.IN) {
						BroadcastMessagePacket broadcast = (BroadcastMessagePacket) packet;
						
						if(broadcast != null) {
							appendToLog(
								cp.parse(broadcast.display(), ChatParser.TYPE_SYSTEM_MESSAGE),
								null,
								null,
								ChatParser.TYPE_SYSTEM_MESSAGE
							);
						}	
					}
					
					/*
					 * Vincinity notices
					 */
					if(packet.getType() == VicinityMessagePacket.TYPE && packet.getDirection() == Packet.Direction.IN) {
						VicinityMessagePacket notice = (VicinityMessagePacket) packet;
						
						if(notice != null) {
							appendToLog(
								cp.parse(notice.getStr(), ChatParser.TYPE_SYSTEM_MESSAGE),
								null,
								null,
								ChatParser.TYPE_SYSTEM_MESSAGE
							);
						}
					}
					
					//System message
					if(packet.getType() == VicinityMessagePacket.TYPE) {
						VicinityMessagePacket system = (VicinityMessagePacket) packet;
						
						if(system != null) {
							appendToLog(
								cp.parse(system.display(), ChatParser.TYPE_SYSTEM_MESSAGE),
								null,
								null,
								ChatParser.TYPE_SYSTEM_MESSAGE
							);
						}
					}
					
					//Friend update
					if(packet.getType() == FriendUpdatePacket.TYPE) {
						FriendUpdatePacket friend = (FriendUpdatePacket) packet;

						if(friend != null) {
							if(friend.isFriend() && friend.getDirection() == Packet.Direction.IN) {
								//Add to online friends list
								//Step trough the friend list, so we don't add a friend more than once
								Iterator<Friend> i = AOBotService.this.onlineFriends.iterator();

								boolean addOnlineFriend = true;
							    while(i.hasNext()) {
							    	Friend tmp = i.next();
							    	
							    	if(tmp.getID() == friend.getCharID()) {
							    		addOnlineFriend = false;
							    		
							    		//Remove offline friends from online list
							    		if(!friend.isOnline()) {
							    			i.remove();
							    		
											//Only show logged off message if character is in the friends list
							    			appendToLog(
												cp.parse(friend.display(
													AOBotService.this.aobot.getCharTable(), 
													AOBotService.this.aobot.getGroupTable()
												), ChatParser.TYPE_SYSTEM_MESSAGE),
												null,
												null,
												ChatParser.TYPE_SYSTEM_MESSAGE
											);
							    		}
							    	}
							    }
								
								if(friend.isOnline() && addOnlineFriend) {
									Friend tempFriend = new Friend();
									tempFriend.setID(friend.getCharID());
									tempFriend.setName(AOBotService.this.aobot.getCharTable().getName(friend.getCharID()));
									tempFriend.setOnline(friend.isOnline());
									
									AOBotService.this.onlineFriends.add(tempFriend);
									
									//Show log on message when character is added to the online list
									appendToLog(
										cp.parse(friend.display(
											AOBotService.this.aobot.getCharTable(), 
											AOBotService.this.aobot.getGroupTable()
										), ChatParser.TYPE_SYSTEM_MESSAGE),
										null,
										null,
										ChatParser.TYPE_SYSTEM_MESSAGE
									);
								}
								
								//Add to all friends list
								//Step trough the friend list, so we don't add a friend more than once
							    Iterator<Friend> y = AOBotService.this.allFriends.iterator();
							    
							    boolean addAllFriend = true;
							    while(y.hasNext()) {
							    	Friend tmp = y.next();
							    	
							    	if(tmp.getID() == friend.getCharID()) {
							    		addAllFriend = false;
							    	}
							    }
								
								if(addAllFriend) {
									Friend tempFriend = new Friend();
									tempFriend.setID(friend.getCharID());
									tempFriend.setName(AOBotService.this.aobot.getCharTable().getName(friend.getCharID()));
									tempFriend.setOnline(friend.isOnline());
									
									AOBotService.this.allFriends.add(tempFriend);									
								}
							}
						}
					}
					
					//Group announcement
					if(packet.getType() == ChannelUpdatePacket.TYPE) {
						ChannelUpdatePacket group = (ChannelUpdatePacket) packet;
												
						//if(!AOBotService.this.groupIgnore.contains(group.getGroupName())) {
							if(!AOBotService.this.groupList.contains(group.getGroupName())) {
								AOBotService.this.groupList.add(group.getGroupName());
							}
						//}
					}
					
					//Private group invitation
					if(packet.getType() == PrivateChannelInvitePacket.TYPE) {
						AOBotService.this.invitation = (PrivateChannelInvitePacket) packet;
						
						if(!AOBotService.this.groupList.contains(PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(AOBotService.this.invitation.getGroupID()))) {
							AOBotService.this.groupList.add(PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(AOBotService.this.invitation.getGroupID()));
						}						
						connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_INVITE);
					    getApplicationContext().sendBroadcast(connectionBroadcast);
					}
					
					//Private group join
					if(packet.getType() == PrivateChannelCharacterJoinPacket.TYPE) {
						PrivateChannelCharacterJoinPacket pgjoin = (PrivateChannelCharacterJoinPacket) packet;
						appendToLog(
							cp.parse(
								"[" + AOBotService.this.aobot.getCharTable().getName(pgjoin.getGroupID()) + "] " + 
								AOBotService.this.aobot.getCharTable().getName(pgjoin.getCharID()) + 
								" " + getString(R.string.group_joined) + ".", ChatParser.TYPE_GROUP_MESSAGE),
							null,
							PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(pgjoin.getGroupID()),
							ChatParser.TYPE_GROUP_MESSAGE
						);
					}
					
					//Private group kick
					if(packet.getType() == PrivateChannelKickPacket.TYPE) {
						PrivateChannelKickPacket pgkick = (PrivateChannelKickPacket) packet;
						appendToLog(
							cp.parse(
								"[" + AOBotService.this.aobot.getCharTable().getName(pgkick.getGroupID()) + 
								"] " + getString(R.string.group_kicked) + ".", 
								ChatParser.TYPE_GROUP_MESSAGE
							),
							null,
							null,
							ChatParser.TYPE_GROUP_MESSAGE
						);
						
						if(AOBotService.this.groupList.contains(PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(AOBotService.this.invitation.getGroupID()))) {
							AOBotService.this.groupList.remove(PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(AOBotService.this.invitation.getGroupID()));
						}
					}
					
					//Private group leave
					if(packet.getType() == PrivateChannelCharacterLeavePacket.TYPE) {
						PrivateChannelCharacterLeavePacket pgleave = (PrivateChannelCharacterLeavePacket) packet;
						appendToLog(
								cp.parse(
									"[" + AOBotService.this.aobot.getCharTable().getName(pgleave.getGroupID()) + "] " + 
									AOBotService.this.aobot.getCharTable().getName(pgleave.getCharID()) + 
									" " + getString(R.string.group_left) + ".",
									ChatParser.TYPE_GROUP_MESSAGE
								),
								null,
								PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(pgleave.getGroupID()),
								ChatParser.TYPE_GROUP_MESSAGE
							);
					}
					
					//Private group message
					if(packet.getType() == PrivateChannelMessagePacket.TYPE) {
						PrivateChannelMessagePacket pgmsg = (PrivateChannelMessagePacket) packet;
						
						if(pgmsg != null) {
							appendToLog(
								cp.parse(pgmsg.display(
									AOBotService.this.aobot.getCharTable(), 
									AOBotService.this.aobot.getGroupTable()
								), ChatParser.TYPE_GROUP_MESSAGE),
								AOBotService.this.aobot.getCharTable().getName(pgmsg.getCharID()),
								PRIVATE_GROUP_PREFIX + AOBotService.this.aobot.getCharTable().getName(pgmsg.getGroupID()),
								ChatParser.TYPE_GROUP_MESSAGE
							);
						}
					}
				}
				
				@Override
				public void started(Bot bot) {
					connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_STARTED);
				    getApplicationContext().sendBroadcast(connectionBroadcast);
				}
			});
		}
		
		if(AOBotService.this.aobot.getState() == Bot.State.DISCONNECTED) {
			connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_SERVER);
		    getApplicationContext().sendBroadcast(connectionBroadcast);
	
		    AOBotService.this.messages.clear();
		}
	}

	
	/**
	 * Disconnect from server
	 */
	public void disconnect() {   
		if(AOBotService.this.aobot != null) {
			if(AOBotService.this.aobot.getState() != Bot.State.DISCONNECTED) {
		    	try {
					AOBotService.this.aobot.disconnect();
					AOBotService.this.afk = false;
					AOBotService.this.aobot = null;
				} catch (IOException e) {
					Log.d(APPTAG, "Failed to disconnect : " + e.getMessage());
				}
		    }
		}
	}

	
	/**
	 * Add message to the message log
	 * @param message
	 * @param character
	 * @param channel
	 * @param type
	 */
	public void appendToLog(String message, String character, String channel, int type) {
		messages.add(new ChatMessage(new Date().getTime(), message,	character, channel, type));
		
		newMessageBroadcast.putExtra(EXTRA_MESSAGE, MSG_UPDATE);
	    getApplicationContext().sendBroadcast(newMessageBroadcast);
	    
	    WidgetController wc = new WidgetController();
	    wc.setText(message, type, this);
	    
    	boolean toWatch = false;
    	boolean vibrate = false;
	    
	    if(type == ChatParser.TYPE_PRIVATE_MESSAGE && message.contains("from [")) {
	    	toWatch = true;
	    	vibrate = true;
	    }
	    
	    if(type == ChatParser.TYPE_SYSTEM_MESSAGE) {
	    	toWatch = true;
	    }
    	
	    if(toWatch) {
	    	watch.pushText(character, channel, message, vibrate);
	    }
	}
	
	
	/**
	 * Send a private message
	 * @param target
	 * @param message
	 * @param lookup
	 * @param log
	 */
	public void sendTell(String target, String message, boolean lookup, boolean log) {
		//Log.d(APPTAG, "NAME: " + target + ", FORMATTED: " + NameFormat.format(target));
		try {
			//if(this.aobot.getCharTable().getID(target) != null) {
			//	this.aobot.sendTell(this.aobot.getCharTable().getID(target), message);				
			//} else {
				this.aobot.sendTell(NameFormat.format(target), message, lookup);
			//}
		} catch (IOException e) {
			Log.d(APPTAG, "Could not send private message : " + e.getMessage());
		}
		
		if(log) {
			appendToLog(
				this.cp.parse(
					getString(R.string.to) + " [" + 
					NameFormat.format(target) + "]: " + message, 
					ChatParser.TYPE_PRIVATE_MESSAGE
				),
				target,
				null,
				ChatParser.TYPE_PRIVATE_MESSAGE
			);
		}
	}
	
	
	public String getCurrentCharacter() {
		return this.aobot.getCharacter().getName();
	}
	
	
	/**
	 * Send a group message
	 * @param target
	 * @param message
	 */
	public void sendGMsg(String target, String message) {	
		try {
			AOBotService.this.aobot.sendChannelMessage(target, message);
		} catch (IOException e) {
			Log.d(APPTAG, "Could not send group message : " + e.getMessage());
		}
	}
	
	
	/**
	 * Send a private group message
	 * @param target
	 * @param message
	 */
	public void sendPGMsg(String channel, String message) {
		try {
			AOBotService.this.aobot.sendPrivateChannelMessage(channel, message);
		} catch (IOException e) {
			Log.d(APPTAG, "Could not send private group message : " + e.getMessage());
		}
	}
	
	
	/**
	 * Set the list of groups that we don't want too listen to
	 * @param groups
	 */
	public void setDisabledGroups(List<String> groups) {
		AOBotService.this.groupDisable = groups;
	}

	
	/**
	 * Sets the list of groups that we want displayed on the watch
	 * Currently not in use
	 * @param groups
	 */
	public void setEnabledWatchGroups(List<String> groups) {
		AOBotService.this.watchEnable = groups;
	}
	
	
	/**
	 * Check if the user is AFK
	 * @return
	 */
	public boolean getAFK() {
		return AOBotService.this.afk;
	}
	
	
	/**
	 * Toggle the AFK mode
	 */
	public void setAFK(boolean onoff) {
		AOBotService.this.afktime = System.currentTimeMillis();
		
		if(AOBotService.this.aobot != null) {
			if(!onoff) {
				//Remove AFK
				AOBotService.this.afk = false;
				
				if(AOBotService.this.aobot.getState() == Bot.State.LOGGED_IN) {
					setNotification(
					    AOBotService.this.aochar.getName() + " " + 
					    getString(R.string.logged_in),
					    true
					);
					
					appendToLog(cp.parse("AFK off.", ChatParser.TYPE_SYSTEM_MESSAGE), null, null, ChatParser.TYPE_SYSTEM_MESSAGE);
				}
			} else {
				//Set as AFK
				AOBotService.this.afk = true;
				
				if(AOBotService.this.aobot.getState() == Bot.State.LOGGED_IN) {
					setNotification(
					    AOBotService.this.aochar.getName() + " " + 
					    getString(R.string.logged_in) + " (AFK)",
					    true
					);
					
					appendToLog(cp.parse(
							"AFK on. All tell messages will be replied with afk.",
							ChatParser.TYPE_SYSTEM_MESSAGE
					), null, null, ChatParser.TYPE_SYSTEM_MESSAGE);
				}
			}
		}
	}
	
	/**
	 * Get the list of disabled groups
	 * @return
	 */
	public List<String> getDisabledGroups() {
		return AOBotService.this.groupDisable;
	}
	
	
	/**
	 * Get the list of channels displayed in the watch
	 * Currently not in use
	 * @return
	 */
	public List<String> getWatchChannels() {
		return AOBotService.this.watchEnable;
	}
	
	
	/**
	 * Get a list of online friends
	 * @return
	 */
	public List<Friend> getOnlineFriends() {
		return AOBotService.this.onlineFriends;
	}
	
	
	/**
	 * Get a list of all friends, both online and offline
	 * @return
	 */
	public List<Friend> getAllFriends() {
		return AOBotService.this.allFriends;
	}
	
	
	/**
	 * Remove all messages in the log
	 */
	public void clearLog() {
		AOBotService.this.messages.clear();
	}
	
	
	/**
	 * Get the character table from the bot
	 */
	public CharacterIDTable getCharTable() {
		return AOBotService.this.aobot.getCharTable();
	}
	
	/**
	 * Get the group table from the bot
	 */
	public GroupTable getGroupTable() {
		return AOBotService.this.aobot.getGroupTable();
	}
	
	/**
	 * Get private group invitation data
	 * @return
	 */
	public PrivateChannelInvitePacket getInvitation() {
		return AOBotService.this.invitation;
	}
	
	
	/**
	 * Accept an invitation to a private group
	 * @param group
	 */
	public void acceptInvitation(int group ) {
		try {
			AOBotService.this.aobot.acceptInvite(group);
		} catch (IOException e) {
			Log.d(APPTAG, "Could not accept group invitation : " + e.getMessage());
		}
	}
	
	
	/**
	 * Reject an invitation to a private group
	 * @param group
	 */
	public void rejectInvitation(int group ) {
		try {
			AOBotService.this.aobot.denyInvite(group);
		} catch (IOException e) {
			Log.d(APPTAG, "Could not deny group invitation : " + e.getMessage());
		}
	}
	
	
	/**
	 * Leave a group
	 * @param group
	 */
	public void leaveGroup(String group) {
		try {
			AOBotService.this.aobot.leaveChannel(group);
		} catch (IOException e) {
			Log.d(APPTAG, "Could not leave group : " + e.getMessage());
		}
	}
	
	
	/**
	 * Get list of messages, make start 0 if you want all
	 * @param start
	 * @return
	 */
	public List<ChatMessage> getLastMessages(int start) {
		if(start < 0) {
			start = 0;
		}
		
		if(start < AOBotService.this.messages.size()) {
			List<ChatMessage> temp = messages.subList(start, AOBotService.this.messages.size());
			return temp;
		} else {
			return null;
		}
	}
	
	
	/**
	 * Get the size of the message log array
	 * @return
	 */
	public int getMessagesSize() {
		return messages.size();
	}
	
	
	/**
	 * Add a friend to the buddy list
	 * @param name
	 */
	public void addFriend(String name) {
		try {
			AOBotService.this.aobot.addFriend(name, true);
		} catch (IOException e) {
			Log.d(APPTAG, "Failed to add friend : " + e.getMessage());
		}
	}

	
	/**
	 * Remove a friend from the buddy list
	 * @param name
	 */
	public void removeFriend(String name) {
		try {
			AOBotService.this.aobot.deleteFriend(name, true);
			
			Iterator<Friend> y = AOBotService.this.allFriends.iterator();
		    int targetpos = -1;
		    int currentpos = -1;
		    
		    while(y.hasNext()) {
		    	currentpos++;
		    	Friend tmp = y.next();
		    	
		    	if(tmp.getName().equals(name)) {
		    		targetpos = currentpos;
		    	}
		    }
		    
		    if(targetpos >= 0) {
		    	AOBotService.this.allFriends.remove(targetpos);
		    }
		} catch (IOException e) {
			Log.d(APPTAG, "Failed to remove friend : " + e.getMessage());
		}
	}
	
	
	/**
	 * Get the packet containing character data
	 * @return
	 */
	public CharacterListPacket getCharPacket() {
		return AOBotService.this.charpacket;
	}
	
	
	/**
	 * Set character to log in with
	 * @param character
	 */
	public void setCharacter(CharacterInfo character) {
		AOBotService.this.aochar = character;
		
		if(AOBotService.this.aobot.getState() == Bot.State.AUTHENTICATED) {
			try {
				AOBotService.this.aobot.login(aochar);		
			} catch (IOException e) {
				Log.d(APPTAG, "Failed to log in : " + e.getMessage());
			}
		} else {
			Log.d(APPTAG, "Failed to log in : Not connected or authenticated");
		}
	}
	
	
	/**
	 * Set server to use
	 * @param server
	 */
	public void setServer(DimensionAddress server) {
		AOBotService.this.aoserver = server;
		
		try {
			AOBotService.this.aobot.connect(AOBotService.this.aoserver);
		} catch (IOException e) {
			Log.d(APPTAG, "Failed to connect : " + e.getMessage());
			
			connectionBroadcast.putExtra(EXTRA_CONNECTION, CON_CFAILURE);
		    getApplicationContext().sendBroadcast(connectionBroadcast);			
		}
	}
	
	
	/**
	 * Set account information
	 * @param username
	 * @param password
	 */
	public void setAccount(String username, String password) {
		AOBotService.this.USERNAME = username;
		AOBotService.this.PASSWORD = password;
		
		if(AOBotService.this.aobot.getState() == Bot.State.CONNECTED) {
			try {
				AOBotService.this.aobot.authenticate(USERNAME, PASSWORD);
			} catch (IOException e) {
				Log.d(APPTAG, "Failed to authenticate : " + e.getMessage());
			}
		} else {
			Log.d(APPTAG, "Failed to authenticate : Not connected");
		}	
	}
	
	
	/**
	 * Get the state of the bot
	 * @return
	 */
	public Bot.State getState() {
		if(AOBotService.this.aobot != null) {
			return AOBotService.this.aobot.getState();
		} else {
			return Bot.State.DISCONNECTED;
		}
	}
	
	
	/**
	 * Get list of available groups
	 * @return
	 */
	public List<String> getGroupList() {
		return AOBotService.this.groupList;
	}
	
	
	/**
	 * Get list of ignored groups
	 * @return
	 */
	public List<String> getGroupIgnoreList() {
		return AOBotService.this.groupIgnore;
	}
	
	
	@Override
	public IBinder onBind(Intent arg0) {
		return new ListenBinder();
	}
	
	
	@Override
	public void onCreate() {
		newMessageBroadcast  = new Intent(INFO_MESSAGE);
		connectionBroadcast  = new Intent(INFO_CONNECTION);
		
		groupList    = new ArrayList<String>();
        groupDisable = new ArrayList<String>();
        watchEnable  = new ArrayList<String>();
        
        messages = new ArrayList<ChatMessage>();
        onlineFriends  = new ArrayList<Friend>();
        allFriends  = new ArrayList<Friend>();
        
        afktime = System.currentTimeMillis();
        
        //Channels that shouldn't be added to the list of available channels
        groupIgnore = new ArrayList<String>();
        groupIgnore.add("Tower Battle Outcome");
        groupIgnore.add("Tour Announcements");
        groupIgnore.add("IRRK News Wire");
        groupIgnore.add("Org Msg");
        groupIgnore.add("All Towers");
        
        cp = new ChatParser();
        watch = new Watch(AOBotService.this);
        
	    noteManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}
	
	@Override
	public void onStart(Intent intent, int startid) {
	    //onStart
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    Log.d(APPTAG, "onStartCommand");
	    return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		//onDestroy
	}
}
