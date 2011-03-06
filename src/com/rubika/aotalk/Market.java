/*
 * Market.java
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.TextView;

public class Market extends Activity {
	protected static final String APPTAG = "--> AOTalk::Market";
	
	private ListView marketlist;
	private List<MarketMessage> marketposts;
	private MarketMessageAdapter msgadapter;
	private long lastfetch = 0;
	private boolean firstrun = true;
	private Handler marketHandler = new Handler();
	private SharedPreferences settings;
	
	private String SERVER = "0";
	private String INTERVAL = "5";
	private boolean UPDATE = true;
	
	private TextView status;
	private ProgressDialog loader;
	private String resultData;

	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        SERVER   = settings.getString("marketserver", SERVER);
        UPDATE   = settings.getBoolean("marketautoupdate", UPDATE);
        INTERVAL = settings.getString("marketinterval", INTERVAL);
       
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.market);
        
        status = (TextView) findViewById(R.id.status);
                
        marketposts = new ArrayList<MarketMessage>();
        
        marketlist = (ListView)findViewById(R.id.market);
        marketlist.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);

        msgadapter = new MarketMessageAdapter(this, marketposts);

        marketlist.setAdapter(msgadapter);
        marketlist.setFocusable(true);
        marketlist.setFocusableInTouchMode(true);
        marketlist.setItemsCanFocus(true);
        
        marketlist.setOnItemLongClickListener(new OnItemLongClickListener(){
			@Override
			public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
	    		return false;
			}
        });
    	
    	loader = new ProgressDialog(this);
    	loader.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	loader.setTitle(getResources().getString(R.string.loading_data));
		loader.setMessage(getResources().getString(R.string.please_wait));
		loader.show();
        
    	marketHandler.post(setLoading);
        getMarketData();
    }
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)  {
	    if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
	        marketHandler.removeCallbacks(Market_Tick);
	        finish();
	        return true;
	    }

	    return super.onKeyDown(keyCode, event);
	}
	
	private Runnable Market_Tick = new Runnable() {
		public void run() {
	    	marketHandler.post(setLoading);
	    	
			getMarketData();
		}
	};
	
	private Runnable setLoading = new Runnable() {
		public void run() {
			status.setText("Loading...");
	    	Log.d(APPTAG, "LOADING");
		}
	};
	
	private Runnable setDone = new Runnable() {
		public void run() {
			String statustext = "";
			
			if(SERVER.equals("0")) {
				statustext = "Atlantean";
			} else if(SERVER.equals("1")) {
				statustext = "Rimor";
			} else {
				statustext = "TestLive";
			}
			
			if(UPDATE) {
				statustext += " (Auto)";
			}
			
			status.setText(statustext);
			loader.dismiss();
			
	    	Log.d(APPTAG, "DONE");
		}
	};
	
	final Runnable outputResult = new Runnable() {
        public void run() {
           	updateResultsInUi();
        }
    };
    
    private void updateResultsInUi() {
    	ChatParser chat = new ChatParser();
    	
    	try{
    		if(!resultData.startsWith("null")) {
    			JSONArray jArray = new JSONArray(resultData);
    				    			
    	        for(int i = jArray.length() - 1; i >= 0; i--){
    	        	JSONObject json_data = jArray.getJSONObject(i);
	                
    	        	int side = 0;
    	        	
	                if(json_data.getInt("omni") == 1) {
	                	side = 1;
	                }
	                
	                if(json_data.getInt("clan") == 1) {
	                	side = 2;
	                }
	                
	                if(json_data.getInt("neut") == 1) {
	                	side = 3;
	                }
	                
	                marketposts.add(new MarketMessage(
                		json_data.getLong("time"),
                		chat.parse(json_data.getString("message"),
                		ChatParser.TYPE_PLAIN_MESSAGE), 
                		json_data.getString("player"), 
                		null,
                		side
	                ));
	                
	                if(json_data.getLong("time") > lastfetch) {
	                	lastfetch = json_data.getLong("time");
	                }
    	        }
    	        
    	    	msgadapter.notifyDataSetChanged();
    	    	
    			if(firstrun) {
    		    	marketlist.setSelection(marketposts.size());
    		    	firstrun = false;
    			}
    		}
    	} catch(JSONException e){
    	        Log.e(APPTAG, "Error parsing data " + e.toString());
    	}
    	marketHandler.post(setDone);
    }	
    
    private void getMarketData() {    	
    	String mode = "?mode=json";
    	String order = "&order=desc";
    	String time = "&time=" + lastfetch; 	
    	String server = "&server=" + SERVER;
    	
    	String limit = "";
    	if(lastfetch == 0) {
    		limit = "&limit=25";
    	}
    	
    	String url = "http://www.rubi-ka.com/market/market.php" + 
		mode +
    	limit + 
		order +
		time +
		server;
    	
    	Log.d(APPTAG, url);
    	
    	//http post
    	try{
	        HttpClient httpclient = new DefaultHttpClient();
	        HttpPost httppost = new HttpPost(url);
	        
	        HttpResponse response = httpclient.execute(httppost);
	        HttpEntity entity = response.getEntity();
	        InputStream is = entity.getContent();
	        
	    	//convert response to string
	    	try{
    			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);
    	        StringBuilder sb = new StringBuilder();
    	        String line = null;
    	        
    	        while ((line = reader.readLine()) != null) {
    	                sb.append(line + "\n");
    	        }
    	        
    	        is.close();
    	 
    	        resultData = sb.toString();
    	        marketHandler.post(outputResult);
	    	} catch(Exception e){
	    	    Log.e(APPTAG, "Error converting result " + e.toString());
	    	}
    	} catch(Exception e){
	        Log.e(APPTAG, "Error in http connection " + e.toString());
    	}
		
    	if(UPDATE) {
			marketHandler.removeCallbacks(Market_Tick);
			marketHandler.postDelayed(Market_Tick, (Integer.parseInt(INTERVAL.trim()) * 1000));
    	}
    	
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
        SERVER   = settings.getString("marketserver", SERVER);
        UPDATE   = settings.getBoolean("marketautoupdate", UPDATE);
        INTERVAL = settings.getString("marketinterval", INTERVAL);
        
    	marketHandler.post(setDone);
		
    	if(UPDATE) {
	        marketHandler.removeCallbacks(Market_Tick);
			marketHandler.postDelayed(Market_Tick, (Integer.parseInt(INTERVAL.trim()) * 1000));
    	}
		
    	Log.d(APPTAG, "RESUME");
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
    	marketHandler.removeCallbacks(Market_Tick);

    	Log.d(APPTAG, "PAUSE");
	}
    
    @Override
    public void onStop() {
    	super.onStop();

		Log.d(APPTAG, "STOP");

    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.marketmenu, menu);
		
        return true;
    }
    
    public boolean onPrepareOptionsMenu(Menu menu) {
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
	        case R.id.update:
	        	getMarketData();
	        	return true;
	        case R.id.settings:
	        	showSettings();
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
        }
    }
    
    private void showSettings() {
    	Intent intent = new Intent(this, MarketSettings.class);
        startActivity(intent);
    }
}
