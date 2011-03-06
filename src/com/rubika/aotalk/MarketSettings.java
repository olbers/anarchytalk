/*
 * MarketSettings.java
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

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;

public class MarketSettings extends PreferenceActivity {
	protected static final String APPTAG = "--> AOTalk::MarketSettings";
	
	@Override 
	protected void onCreate(Bundle savedInstanceState) { 
	    super.onCreate(savedInstanceState); 
	    
	    PreferenceScreen screen = createPreferenceHierarchy();
	    setPreferenceScreen(screen); 
	}
	
	private PreferenceScreen createPreferenceHierarchy() {        
	    PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

	    //Account settings
	    PreferenceCategory accountCat = new PreferenceCategory(this);
	    accountCat.setTitle(getString(R.string.market));
        root.addPreference(accountCat);
        
        ListPreference server = new ListPreference(this);
        server.setKey("marketserver");
        server.setTitle(getString(R.string.market_server));
        server.setSummary(getString(R.string.market_server_info));
        server.setEntries(R.array.servers);
        server.setEntryValues(R.array.servers_values);
        server.setDefaultValue("0");
        root.addPreference(server);
        
        EditTextPreference interval = new EditTextPreference(this);
        interval.setKey("marketinterval");
        interval.setTitle(getString(R.string.market_interval));
        interval.setSummary(getString(R.string.market_interval_info));
        interval.setDefaultValue("5");
        root.addPreference(interval);
        
        CheckBoxPreference autoupdate = new CheckBoxPreference(this);
        autoupdate.setKey("marketautoupdate");
        autoupdate.setTitle(getString(R.string.market_autoupdate));
        autoupdate.setSummary(getString(R.string.market_autoupdate_info));
        autoupdate.setDefaultValue(true);
        root.addPreference(autoupdate);
        
	    return root; 
	}
}