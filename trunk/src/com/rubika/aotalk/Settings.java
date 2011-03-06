/*
 * Settings.java
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

import java.util.List;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import ao.protocol.Bot;

public class Settings extends PreferenceActivity {
	protected static final String APPTAG = "--> AOTalk::Settings";
	private ServiceConnection conn;
	private AOBotService bot;
	
	@Override 
	protected void onCreate(Bundle savedInstanceState) { 
	    super.onCreate(savedInstanceState); 
	    
	    //addPreferencesFromResource(R.xml.preferences);
	    
	    attachToService();
	    
	    PreferenceScreen screen = createPreferenceHierarchy();
	    setPreferenceScreen(screen); 
	} 
	
	private PreferenceScreen createPreferenceHierarchy() {        
	    PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

        PreferenceScreen about = getPreferenceManager().createPreferenceScreen(this);
        about.setKey("about_button");
        about.setTitle(getString(R.string.aboutaotalk));
        about.setSummary(getString(R.string.aboutaotalk_about));
        about.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
		    	Intent intent = new Intent(Settings.this, About.class);
		        startActivity(intent);

				return false;
			}
	    });
	    root.addPreference(about);
       
 	    //Account settings
	    PreferenceCategory accountCat = new PreferenceCategory(this);
	    accountCat.setTitle(getString(R.string.account));
        root.addPreference(accountCat);
        
        EditTextPreference username = new EditTextPreference(this);
        username.setDialogTitle(getString(R.string.set_username));
        username.setKey("username");
        username.setTitle(getString(R.string.username));
        username.setSummary(getString(R.string.edit_username));
        root.addPreference(username);
        
        EditTextPreference password = new EditTextPreference(this);
        password.setDialogTitle(getString(R.string.set_password));
        password.setKey("password");
        password.setTitle(getString(R.string.password));
        password.setSummary(getString(R.string.edit_password));
        password.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addPreference(password);
        
        CheckBoxPreference saveAccount = new CheckBoxPreference(this);
        saveAccount.setKey("savepref");
        saveAccount.setTitle(getString(R.string.save_account));
        saveAccount.setSummary(getString(R.string.save_account_info));
        root.addPreference(saveAccount);
	    
	    //Channel settings
        PreferenceCategory channelsCat = new PreferenceCategory(this);
        channelsCat.setTitle(getString(R.string.channels));
        root.addPreference(channelsCat);
        
	    PreferenceScreen muteChannels = getPreferenceManager().createPreferenceScreen(this);
	    muteChannels.setKey("disabledchannels_button");
	    muteChannels.setTitle(getString(R.string.mute_channels));
	    muteChannels.setSummary(getString(R.string.select_channels_to_mute));
	    muteChannels.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				handleChannels();
				return false;
			}
	    });
	    root.addPreference(muteChannels);
	    
	    //Friend settings
	    PreferenceCategory friendsCat = new PreferenceCategory(this);
	    friendsCat.setTitle(getString(R.string.friends));
        root.addPreference(friendsCat);
        
	    PreferenceScreen addFriend = getPreferenceManager().createPreferenceScreen(this);
	    addFriend.setKey("addfriend_button");
	    addFriend.setTitle(getString(R.string.add_friend));
	    addFriend.setSummary(getString(R.string.add_friend_to_buddy_list));
	    addFriend.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				addFriend();
				return false;
			}
	    });
	    root.addPreference(addFriend); 
        
	    PreferenceScreen removeFriend = getPreferenceManager().createPreferenceScreen(this);
	    removeFriend.setKey("addfriend_button");
	    removeFriend.setTitle(getString(R.string.remove_friend));
	    removeFriend.setSummary(getString(R.string.remove_friend_from_buddy_list));
	    removeFriend.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				removeFriend();
				return false;
			}
	    });
	    root.addPreference(removeFriend); 
	    
	    //Other settings
	    PreferenceCategory otherCat = new PreferenceCategory(this);
	    otherCat.setTitle(getString(R.string.other));
        root.addPreference(otherCat);
        
        CheckBoxPreference autoAFK = new CheckBoxPreference(this);
        autoAFK.setKey("autoafk");
        autoAFK.setTitle(getString(R.string.auto_afk));
        autoAFK.setSummary(getString(R.string.go_afk_when));
        root.addPreference(autoAFK);
        
        CheckBoxPreference enableWatch = new CheckBoxPreference(this);
        enableWatch.setKey("enablewatch");
        enableWatch.setTitle(getString(R.string.enable_watch));
        enableWatch.setSummary(getString(R.string.show_messages_on_watch));
        root.addPreference(enableWatch);
        
        /*
	    PreferenceScreen watchChannels = getPreferenceManager().createPreferenceScreen(this);
	    watchChannels.setKey("watchchannels_button");
	    watchChannels.setTitle("Set channels to show in watch");
	    watchChannels.setSummary("Select the channels you want to display in your watch");
	    watchChannels.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference arg0) {
				handleWatchChannels();
				return false;
			}
	    });
	    root.addPreference(watchChannels);
        */
        
	    return root; 
	}
	
	private void addFriend() {
	   	final ChatParser cp = new ChatParser();
	   	
		LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(R.layout.addfriend,(ViewGroup) findViewById(R.layout.addfriend));
    	Builder builder = new AlertDialog.Builder(Settings.this);
    	builder.setTitle(getString(R.string.add_a_friend));
    	builder.setView(layout);

    	builder.setPositiveButton(getString(R.string.add), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {  	                											
				EditText name = (EditText) layout.findViewById(R.id.friendname);
				if(Settings.this.bot.getState() != Bot.State.DISCONNECTED) {
					Settings.this.bot.addFriend(name.getText().toString());
					bot.appendToLog(
						cp.parse(name.getText().toString() + " " + getString(R.string.added_to_buddy_list), ChatParser.TYPE_SYSTEM_MESSAGE),
						null,
						null,
						ChatParser.TYPE_SYSTEM_MESSAGE
					);
				}
				return;
			} 
		});
		
    	builder.setNegativeButton(Settings.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				//Do nothing.
				return;
			}
		}); 
    	
    	AlertDialog addfriendbox = builder.create();
    	addfriendbox.show();
	}
	
	private void removeFriend() {
    	final ChatParser cp = new ChatParser();
		final List<Friend> friendList = Settings.this.bot.getAllFriends();
    	
    	CharSequence[] tempList = null;
    	
    	if(friendList != null) {
    		tempList = new CharSequence[friendList.size()];
    		
	    	for(int i = 0; i < friendList.size(); i++) {
	    		tempList[i] = friendList.get(i).getName();
	    	}
    	}
     	
    	final CharSequence[] flist = tempList;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(getString(R.string.remove_friend));
    	
    	builder.setItems(flist, new DialogInterface.OnClickListener() {
    	    @Override
			public void onClick(DialogInterface dialog, int which) {
    	    	final String fname = flist[which].toString();
    	    	
    	    	AlertDialog acceptRemoveDialog = new AlertDialog.Builder(Settings.this).create();
    	    	
    	    	acceptRemoveDialog.setTitle(getString(R.string.remove) + " " + fname);
    	    	acceptRemoveDialog.setMessage(
    	    			getString(R.string.remove_friend_confirm1)
    	    			+ " " + fname + " " + 
    	    			getString(R.string.remove_friend_confirm2)
    	    	);
    	    		            
    	    	acceptRemoveDialog.setButton(Settings.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) {
    					Settings.this.bot.removeFriend(fname);
    					bot.appendToLog(
    						cp.parse(fname + " " + getString(R.string.removed_from_buddy_list), ChatParser.TYPE_SYSTEM_MESSAGE),
    						null,
    						null,
    						ChatParser.TYPE_SYSTEM_MESSAGE
    					);
    					return;
    				} 
    			});
    			
    	    	acceptRemoveDialog.setButton2(Settings.this.getString(R.string.cancel), new DialogInterface.OnClickListener() {
    				public void onClick(DialogInterface dialog, int which) {
    					//Do nothing.
    					return;
    				}
    			}); 
    	    	
    	    	acceptRemoveDialog.show();
			}
    	});
    	
    	builder.setNegativeButton(this.getString(R.string.cancel), new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//Do nothing.
			}
    	});
    	
    	AlertDialog settingsbox = builder.create();
    	settingsbox.show();	
	}
	
    private void handleChannels() {   	
    	final List<String> groupDisable = Settings.this.bot.getDisabledGroups();
    	final List<String> groupList = Settings.this.bot.getGroupList();
    	
    	CharSequence[] tempChannels = null;
    	boolean[] channelStates = null;
    	
    	if(groupList != null) {
    		tempChannels = new CharSequence[groupList.size()];
    		channelStates = new boolean[groupList.size()];
    		
	    	for(int i = 0; i < groupList.size(); i++) {
	    		tempChannels[i] = groupList.get(i);
				if(groupDisable != null ) {
		    		if(groupDisable.contains(groupList.get(i))) {
						channelStates[i] = true;
					} else {
						channelStates[i] = false;
					}
				} else {
					channelStates[i] = false;
				}
	    	} 
    	}
     	
    	final CharSequence[] channellist = tempChannels;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(Settings.this.getString(R.string.disable_channels));
    	
    	builder.setMultiChoiceItems(channellist, channelStates, new DialogInterface.OnMultiChoiceClickListener() {
    	    @Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				if(isChecked) {
					if(!groupDisable.contains(channellist[which].toString())) {
						groupDisable.add(channellist[which].toString());
					}
				} else {
					if(groupDisable.contains(channellist[which].toString())) {
						groupDisable.remove(channellist[which].toString());
					}
				}
				
				Settings.this.bot.setDisabledGroups(groupDisable);
			}
    	});
    	
    	builder.setPositiveButton(Settings.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {  	                						
				Settings.this.bot.setDisabledGroups(groupDisable);
				return;
			} 
		});
    	
    	AlertDialog settingsbox = builder.create();
    	settingsbox.show();	
    }
    
    /*
    private void handleWatchChannels() {   	
    	final List<String> watchEnable = Settings.this.bot.getWatchChannels();
    	//final List<String> groupList = Settings.this.bot.getGroupList();
    	List<String> groupList = new ArrayList<String>();
    	groupList.add(Watch.CHN_SYSTEM);
    	groupList.add(Watch.CHN_PRIVATE);
    	groupList.add(Watch.CHN_OTHERS);
    	
    	CharSequence[] tempChannels = null;
    	boolean[] channelStates = null;
    	
    	if(groupList != null) {
    		tempChannels = new CharSequence[groupList.size()];
    		channelStates = new boolean[groupList.size()];
    		
	    	for(int i = 0; i < groupList.size(); i++) {
	    		tempChannels[i] = groupList.get(i);
				if(watchEnable != null ) {
		    		if(watchEnable.contains(groupList.get(i))) {
						channelStates[i] = true;
					} else {
						channelStates[i] = false;
					}
				} else {
					channelStates[i] = false;
				}
	    	} 
    	}
     	
    	final CharSequence[] channellist = tempChannels;

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(Settings.this.getString(R.string.watch_channels));
    	
    	builder.setMultiChoiceItems(channellist, channelStates, new DialogInterface.OnMultiChoiceClickListener() {
    	    @Override
			public void onClick(DialogInterface dialog, int which, boolean isChecked) {
				if(isChecked) {
					if(!watchEnable.contains(channellist[which].toString())) {
						watchEnable.add(channellist[which].toString());
					}
				} else {
					if(watchEnable.contains(channellist[which].toString())) {
						watchEnable.remove(channellist[which].toString());
					}
				}
				
				Settings.this.bot.setEnabledWatchGroups(watchEnable);
			}
    	});
    	
    	builder.setPositiveButton(Settings.this.getString(R.string.ok), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {  	                						
				Settings.this.bot.setEnabledWatchGroups(watchEnable);
				return;
			} 
		});
    	
    	AlertDialog settingsbox = builder.create();
    	settingsbox.show();	
    }
    */
    
	private void attachToService() {
		Intent serviceIntent = new Intent(this, AOBotService.class);
	    
	    conn = new ServiceConnection() {  	
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				Settings.this.bot = ((AOBotService.ListenBinder) service).getService();
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				Settings.this.bot = null;
			}
	    };

	    this.getApplicationContext().startService(serviceIntent);
	    this.getApplicationContext().bindService(serviceIntent, conn, 0);
	}
}