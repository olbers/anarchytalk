/*
 * AOU.java
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

public class AOU extends Activity {
	protected static final String APPTAG = "--> AOTalk::AOU";
	protected static final String WEBURL = "http://www.ao-universe.com/mobile/index.php?aotalk=1";
	
	private String LASTURL;
	private WebView aouweb;
	private ProgressBar loadingBar;
	private SharedPreferences settings;
	private SharedPreferences.Editor editor;
	
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.aou);
        
        loadingBar = (ProgressBar)findViewById(R.id.progressbar);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        editor = settings.edit();
        
        LASTURL = settings.getString("lasturl", WEBURL);

        aouweb = (WebView) findViewById(R.id.aou);
        
        aouweb.setWebChromeClient(new WebChromeClient() {
			public void onProgressChanged(WebView view, int progress) {
		        loadingBar.setProgress(progress);
		        
		        // hide the progress bar if the loading is complete
		        if (progress == 100) {
		        	loadingBar.setVisibility(View.GONE);
		        
		        } else{
		        	loadingBar.setVisibility(View.VISIBLE);
		        }				
			}
        });
        
        aouweb.setWebViewClient(new AOUWebViewClient());
        aouweb.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        aouweb.setBackgroundColor(Color.parseColor("#062c36"));
        
        WebSettings aousettings = aouweb.getSettings();
        aousettings.setJavaScriptEnabled(true);
        
        // Set up an interface to the Android functions from the web
        aouweb.addJavascriptInterface(new JavaScriptInterface(this), "AOUApp");
        
        // Dont go back to adding things to calendar, we dont want it more than once..
        if(LASTURL.contains("gcal.php")) {
        	LASTURL = "http://www.ao-universe.com/mobile/calendar.php?aotalk=1";
        }
        
        aouweb.loadUrl(LASTURL);
	}
	
    @Override
    public void onResume() {
    	super.onResume();
    	
        LASTURL = settings.getString("lasturl", WEBURL);   	
    }
    
    @Override
    public void onPause() {
        super.onPause();
        
        editor.putString("lasturl", LASTURL);
		editor.commit();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	
    	aouweb.clearCache(true);
    	
        editor.putString("lasturl", LASTURL);
		editor.commit();
    }
    
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    // Check if the key event was the BACK key and if there's history
	    /*
		if ((keyCode == KeyEvent.KEYCODE_BACK) && aouweb.canGoBack()) {
	    	aouweb.goBack();
	        return true;
	    }
	    */
	    
	    // Open/Close the menu when pressing the SEARCH key
	    if ((keyCode == KeyEvent.KEYCODE_SEARCH)) {
	    	aouweb.loadUrl("javascript:toggleMenu(true);");
	        return true;
	    }

	    return super.onKeyDown(keyCode, event);
	}
	
    public class JavaScriptInterface {
        Context mContext;

        JavaScriptInterface(Context c) {
            mContext = c;
        }

        public void historyBack() {
        	aouweb.goBack();
        }
        
        public void reloadLast() {
        	aouweb.loadUrl(LASTURL);
        }
   }
	
	private class AOUWebViewClient extends WebViewClient {
	    @Override
	    public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(APPTAG, url);
            
            // Let aouweb load the page (if it's in the domain ao-universe.com or google.com)
	        if(
	        	url.contains("www.ao-universe.com/mobile") 
	        	||
	        	url.contains("http://www.google.com")
	        	||
	        	url.contains("https://www.google.com")
	        ) {
	        	LASTURL = url;
	        	return false;
	        }
	        
	        // Otherwise launch another Activity that handles URLs
	        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
	        startActivity(intent);
	        
	        return true;
	    }
	    
	    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
	        super.onReceivedError(view, errorCode, description, failingUrl);

	        aouweb.loadUrl("file:///android_asset/error.html");
	    }
	}
	
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.aoumenu, menu);
		
        return true;
    }
    
    public boolean onPrepareOptionsMenu(Menu menu) {
		return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
	        case R.id.start:
	        	aouweb.loadUrl("http://www.ao-universe.com/mobile/index.php?aotalk=1");
	        	return true;
	        case R.id.calendar:
	        	aouweb.loadUrl("http://www.ao-universe.com/mobile/calendar.php?aotalk=1");
	        	return true;
	        case R.id.guides:
	        	aouweb.loadUrl("http://www.ao-universe.com/mobile/guides.php?aotalk=1");
	        	return true;
	        case R.id.update:
	        	aouweb.reload();
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
        }
    }
}
