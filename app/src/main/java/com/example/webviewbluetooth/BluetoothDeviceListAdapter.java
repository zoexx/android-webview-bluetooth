package com.example.webviewbluetooth;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.inuker.bluetooth.library.search.SearchResult;

import java.util.LinkedList;

public class BluetoothDeviceListAdapter extends BaseAdapter {
    private LinkedList<SearchResult> mData;
    private Context mContext;

    public BluetoothDeviceListAdapter(LinkedList<SearchResult> mData, Context mContext){
        this.mData = mData;
        this.mContext = mContext;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public SearchResult getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(mContext).inflate(R.layout.device_item, parent, false);
        TextView name = convertView.findViewById(R.id.name);
        TextView address = convertView.findViewById(R.id.address);
        SearchResult device = mData.get(position);
        name.setText(device.getName());
        address.setText(device.getAddress());
        return convertView;
    }
}
