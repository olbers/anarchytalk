/*
 * Information.java
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import ao.misc.NameFormat;

public class Information extends Activity {
	protected static final String APPTAG    = "--> AOTalk::ShowInfo";
	protected static final String CMD_START = "/start";
	protected static final String CMD_TELL  = "/tell";
	protected static final String CMD_CC    = "/cc";
	
	protected static final String CC_ADD = "addbuddy";
	protected static final String CC_REM = "rembuddy";
	
	protected static final String HTML_START = 
		"<html><head></head><style type=\"text/css\">" +
		"body { background-color:#062c36; color:#ffffff; font-size:0.9em; }" +
		"a { color:#4444ff; }" +
		".item { float:right; }" +
		".icon { margin:0 5px 0 0; position:relative; top:-2px; vertical-align:middle; }" +
		"</style><body>";
	protected static final String HTML_END   = "</body></html>";

	private ServiceConnection conn;
	private AOBotService bot;
	
	private String chatcmd;
	private String target;
	private String method;
	private String message;
	private String resultData;
	
	private ProgressDialog loader;
	private WebView info;
	
	final Handler resultHandler = new Handler();
	
	final Runnable outputResult = new Runnable() {
        public void run() {
           	updateResultsInUi();
        }
    };
    
    private void updateResultsInUi() {
        String res = "";
        
    	if(resultData != null) {
        	if(resultData.length() > 0) {
    	        res = HTML_START + resultData + HTML_END;
        	} else {
        		res = HTML_START + getString(R.string.no_data).replace("\n", "<br />") + HTML_END;
        	}
        } else {
    		res = HTML_START + getString(R.string.no_data).replace("\n", "<br />") + HTML_END;
        }
        
    	info.loadData(Uri.encode(res), "text/html", "UTF-8");
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.showinfo);
        
        attachToService();

        info = (WebView) findViewById(R.id.web);
        info.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        info.setBackgroundColor(Color.parseColor("#062c36"));
        
        info.setWebViewClient(new WebViewClient() {  
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
	    		Log.d(APPTAG, Uri.decode(url));
	    		
	    		//Intercept chatcmd links
	    		if(url.startsWith("chatcmd://")) {
	    	        String command = url.replace("chatcmd://", "").trim();
	    	        command = Uri.decode(command);
	    	        
	    	        chatcmd = command.substring(0, command.indexOf(" ")).trim();
	    	        
	    	        if(chatcmd.equals(CMD_START)) {
	    	        	String openurl = command.replace(chatcmd, "").trim();
	    	        	Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(openurl));
	    	        	startActivity(i);
	    	        	
	    	        	finish();
	    	        } else if(chatcmd.equals(CMD_TELL)) {
	    	        	target  = command.replace(chatcmd, "").trim().substring(0, command.trim().indexOf(" ") + 1).trim();
	    	        	message = command.replace(chatcmd, "").trim().replace(target, "").trim();
	    	        	
	    	        	if(target != null && message != null && bot != null) {
							bot.sendTell(target, message, true, true);
							finish();
						}
	    	        } else if(chatcmd.equals(CMD_CC)) {
	    	        	String[] temp = command.replace(chatcmd, "").trim().split(" ");
	    	        	method = temp[0].trim();
	    	        	target = temp[1].trim();
	    	        	
	    	        	if(target != null && method != null && bot != null) {
							ChatParser cp = new ChatParser();
							
							if(!NameFormat.format(target).equals(NameFormat.format(bot.getCurrentCharacter()))) {
								
								if(method.equals(CC_ADD)) {
									//add a friend
									bot.addFriend(target);
									bot.appendToLog(
										cp.parse(target + getString(R.string.added_to_buddy_list), ChatParser.TYPE_SYSTEM_MESSAGE),
										null,
										null,
										ChatParser.TYPE_SYSTEM_MESSAGE
									);
								}
								
								if(method.equals(CC_REM)) {
									//remove a friend
									bot.removeFriend(target);
									bot.appendToLog(
										cp.parse(target + getString(R.string.removed_from_buddy_list), ChatParser.TYPE_SYSTEM_MESSAGE),
										null,
										null,
										ChatParser.TYPE_SYSTEM_MESSAGE
									);
								}
							} else {
								bot.appendToLog(
									cp.parse(getString(R.string.not_add_yourself), ChatParser.TYPE_SYSTEM_MESSAGE),
									null,
									null,
									ChatParser.TYPE_SYSTEM_MESSAGE
								);
							}

							finish();
						}
	    	        } else {
	    		        info.loadData(HTML_START + getString(R.string.chatcmd_not_implemented) + "<br />'" + chatcmd + "'" + HTML_END, "text/html", "UTF-8");
	    	        }
	            } else {
					Intent infoIntent = new Intent(Information.this, Information.class);
					infoIntent.setData(Uri.parse(url));
					Information.this.startActivity(infoIntent);
	    		}
				
				return true;
            }
        });
        
        
        //Show text data in webview
        if(
        	getIntent().getData().toString().startsWith("text://") || 
        	getIntent().getData().toString().startsWith("charref://")
        ) {
	        String text = getIntent().getData().toString().trim().replace("\n", "<br />").replaceFirst("text://", "");
	        
	        if(text.startsWith("<br />")) {
	        	text = text.replaceFirst("<br />", "");
	        }
	        
	        text = Uri.decode(text);
	        
	        //Use icons from rubi-ka.com
	        Pattern pattern = Pattern.compile("<img src=\'?rdb://([0-9]*?)\'?>");
	        Matcher matcher = pattern.matcher(text);
	        
	        while(matcher.find()) {
	        	text = text.replace(
		        	"<img src=rdb://" + matcher.group(1) + ">", 
		        	"<img src=\"http://www.rubi-ka.com/image/icon/" + matcher.group(1) + ".gif\" class=\"icon\">"
	        	);
	        	
	        	text = text.replace(
			        "<img src='rdb://" + matcher.group(1) + "'>", 
			        "<img src=\"http://www.rubi-ka.com/image/icon/" + matcher.group(1) + ".gif\" class=\"icon\">"
		        );	        	
	        }
	        
	        //Remove UI_GFX, don't know how to match them to a file.
	        pattern = Pattern.compile("<img src=\'?tdb://(.*?)\'?>");
	        matcher = pattern.matcher(text);
	        
	        while(matcher.find()) {
	        	text = text.replace(
	        		"<img src=tdb://" + matcher.group(1) + ">", ""
	        		//"<img src=http://www.rubi-ka.com/image/texture/" + matcher.group(1) + ">"
	        	);
	        }	        
	        
	        info.loadData(HTML_START + text + HTML_END, "text/html", "UTF-8");
        }
        
        
        //Load item information
        if(getIntent().getData().toString().startsWith("itemref://")) {
        	String valuestr = Uri.decode(getIntent().getData().toString()).replace("itemref://", "").trim();
        	String values[] = valuestr.split("/");
        	
        	final String lowid  = values[0];
        	final String itemql = values[2];
        	
        	final ItemRef iref = new ItemRef();
        	
        	Log.d(APPTAG, "DATA : " + itemql + ", " + lowid);
        	
        	loader = new ProgressDialog(this);
	    	loader.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	    	loader.setTitle(getResources().getString(R.string.loading_data));
			loader.setMessage(getResources().getString(R.string.please_wait));
			loader.show();
			
			new Thread() {
	            public void run() {
	            	resultData = iref.getData(lowid, itemql);
	                resultHandler.post(outputResult);
			        
			        loader.dismiss();
	        	}
			}.start();
        }
    }
    
	private void attachToService() {
		Intent serviceIntent = new Intent(this, AOBotService.class);
	    
	    conn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				bot = ((AOBotService.ListenBinder) service).getService();
			}
		
			@Override
			public void onServiceDisconnected(ComponentName name) {
				bot = null;
			}
	    };

	    this.getApplicationContext().startService(serviceIntent);
	    this.getApplicationContext().bindService(serviceIntent, conn, 0);
	}
}
