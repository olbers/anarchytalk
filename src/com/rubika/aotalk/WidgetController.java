/*
 * WidgetController.java
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

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.text.Html;
import android.widget.RemoteViews;

public class WidgetController {
	private AppWidgetManager manager;
	
	/**
	 * Set text and color of the widgets
	 * @param message
	 * @param context
	 */
	public void setText(String message, int type, Context context) {
        manager = AppWidgetManager.getInstance(context);
        
		//Handle text colors
        String color = "#FFFFFF";
        
		switch(type) {
			case ChatParser.TYPE_SYSTEM_MESSAGE:
				color = "#FFCC33";
				break;
			case ChatParser.TYPE_PRIVATE_MESSAGE:
				color = "#88FF88";
				break;
			case ChatParser.TYPE_CLIENT_MESSAGE:
				color = "#CC99CC";
				break;
			case ChatParser.TYPE_GROUP_MESSAGE:
				color = "#FFFFFF";
				break;
		}
        
        //Small widget
        RemoteViews smallWidgetViews = new RemoteViews(context.getPackageName(), R.layout.widget_small);
        
        smallWidgetViews.setTextViewText(R.id.widget_text, Html.fromHtml(message));
        smallWidgetViews.setTextColor(R.id.widget_text, Color.parseColor(color));    
        
        ComponentName smallWidget = new ComponentName(context, WidgetSmall.class);
        
        manager.updateAppWidget(smallWidget, smallWidgetViews);
        
        //Large widget
        RemoteViews largeWidgetViews = new RemoteViews(context.getPackageName(), R.layout.widget_large);
        
        largeWidgetViews.setTextViewText(R.id.widget_text, Html.fromHtml(message));
        largeWidgetViews.setTextColor(R.id.widget_text, Color.parseColor(color));  
        
        ComponentName largeWidget = new ComponentName(context, WidgetLarge.class);
        
        manager.updateAppWidget(largeWidget, largeWidgetViews);
	}
}
