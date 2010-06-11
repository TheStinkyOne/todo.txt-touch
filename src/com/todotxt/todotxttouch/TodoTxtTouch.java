package com.todotxt.todotxttouch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.todotxt.todotxttouch.Util.OnMultiChoiceDialogListener;

public class TodoTxtTouch extends ListActivity implements OnSharedPreferenceChangeListener {

	private final static String TAG = TodoTxtTouch.class.getSimpleName();
	
	private final static int MENU_REFRESH_ID = 0;
	private final static int MENU_SETTINGS_ID = 1;
	private final static int MENU_PRIORITY_ID = 2;
	private final static int MENU_CONTEXT_ID = 3;
	
	private SharedPreferences m_prefs;
	private ProgressDialog m_ProgressDialog = null;
	private ArrayList<Task> m_tasks = null;
	private TaskAdapter m_adapter;
	private String m_fileUrl;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		m_tasks = new ArrayList<Task>();
		m_adapter = new TaskAdapter(this, R.layout.list_item, m_tasks,
				getLayoutInflater());

		setListAdapter(this.m_adapter);

		//FIXME ?
		ListView lv = getListView();
		lv.setTextFilterEnabled(true);

		// Get the xml/preferences.xml preferences
		m_prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		m_prefs.registerOnSharedPreferenceChangeListener(this);
		String defValue = getString(R.string.todourl_default);
		m_fileUrl = m_prefs.getString(getString(R.string.todourl_key), defValue);
		populate();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		m_prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(Menu.NONE, MENU_PRIORITY_ID, Menu.NONE, R.string.priority);
        item.setIcon(android.R.drawable.ic_menu_mylocation);
        item = menu.add(Menu.NONE, MENU_CONTEXT_ID, Menu.NONE, R.string.context);
        item.setIcon(android.R.drawable.ic_menu_myplaces);
        item = menu.add(Menu.NONE, MENU_SETTINGS_ID, Menu.NONE, R.string.settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);
        item = menu.add(Menu.NONE, MENU_REFRESH_ID, Menu.NONE, R.string.refresh);
        item.setIcon(android.R.drawable.ic_menu_rotate);
		return super.onCreateOptionsMenu(menu);
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        if(MENU_SETTINGS_ID == id) {
			Intent settingsActivity = new Intent(this, Preferences.class);
			startActivity(settingsActivity);
        }else if(MENU_REFRESH_ID == id){
        	populate();
        }else if(MENU_PRIORITY_ID == id){
			showDialog(MENU_PRIORITY_ID);
        }else if(MENU_CONTEXT_ID == id){
			showDialog(MENU_CONTEXT_ID);
        }else{
    		return super.onMenuItemSelected(featureId, item);
        }
        return true;
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case MENU_PRIORITY_ID:
			Set<String> prios = TaskHelper.getPrios(m_tasks);
			final List<String> pStrs = new ArrayList<String>(prios);
			return Util.createMultiChoiceDialog(this, pStrs
					.toArray(new String[prios.size()]), null, null, null,
					new OnMultiChoiceDialogListener() {
						@Override
						public void onClick(boolean[] selected) {
							List<Integer> pInts = new ArrayList<Integer>();
							for (int i = 0; i < selected.length; i++) {
								if(selected[i]){
									pInts.add(TaskHelper.parsePrio(pStrs.get(i)));
								}
							}
							List<Task> items = TaskHelper.getByPrio(m_tasks, pInts);
							TodoUtil.setTasks(m_adapter, items);
						}
					});
		case MENU_CONTEXT_ID:
			Set<String> contexts = TaskHelper.getContexts(m_tasks);
			final List<String> cStrs = new ArrayList<String>(contexts);
			return Util.createMultiChoiceDialog(this, cStrs
					.toArray(new String[contexts.size()]), null, null, null,
					new OnMultiChoiceDialogListener() {
						@Override
						public void onClick(boolean[] selected) {
							List<String> cStrs2 = new ArrayList<String>();
							for (int i = 0; i < selected.length; i++) {
								if(selected[i]){
									cStrs2.add(cStrs.get(i));
								}
							}
							List<Task> items = TaskHelper.getByContext(m_tasks, cStrs2);
							TodoUtil.setTasks(m_adapter, items);
						}
					});
		}
		return null;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Task item = m_adapter.items.get(position);
		Util.showDialog(this, R.string.app_name, item.toString());
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		Log.v(TAG, "onSharedPreferenceChanged key="+key);
		if(getString(R.string.todourl_key).equals(key)) {
			String defValue = getString(R.string.todourl_default);
			m_fileUrl = sharedPreferences.getString(key, defValue);
			populate();
		}
	}

	private void populate(){
    	new AsyncTask<Void, Void, Void>(){
    		@Override
    		protected void onPreExecute() {
    			m_ProgressDialog = ProgressDialog.show(TodoTxtTouch.this,
    					"Please wait...", "Retrieving todo.txt ...", true);
    		}
			@Override
			protected Void doInBackground(Void... params) {
				try {
					m_tasks = TodoUtil.loadTasksFromUrl(TodoTxtTouch.this, m_fileUrl);
				} catch (IOException e) {
					Log.e(TAG, "BACKGROUND_PROC "+ e.getMessage());
					Util.showToastLong(TodoTxtTouch.this, e.getMessage());
				}
				return null;
			}
    		@Override
    		protected void onPostExecute(Void result) {
    			m_ProgressDialog.dismiss();
    			TodoUtil.setTasks(m_adapter, m_tasks);
    		}
    	}.execute();
	}

	public class TaskAdapter extends ArrayAdapter<Task> {

		private ArrayList<Task> items;
		
		private LayoutInflater m_inflater;

		public TaskAdapter(Context context, int textViewResourceId,
				ArrayList<Task> items, LayoutInflater inflater) {
			super(context, textViewResourceId, items);
			this.items = items;
			this.m_inflater = inflater;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final ViewHolder holder;
			if(convertView == null){
				convertView = m_inflater.inflate(R.layout.list_item, null);
				holder = new ViewHolder();
				holder.taskid = (TextView) convertView.findViewById(R.id.taskid);
				holder.taskprio = (TextView) convertView.findViewById(R.id.taskprio);
				holder.tasktext = (TextView) convertView.findViewById(R.id.tasktext);
				holder.taskcontexts = (TextView) convertView.findViewById(R.id.taskcontexts);
				convertView.setTag(holder);
			}else{
				holder = (ViewHolder) convertView.getTag();
			}
			Task task = items.get(position);
			if (task != null) {
				holder.taskid.setText(String.format("%04d", task.id));
				holder.taskprio.setText("("+TaskHelper.toString(task.prio)+")");
				holder.tasktext.setText(task.taskDescription);
				holder.taskcontexts.setText(task.getContextsAsString());
				
				switch (task.prio) {
				case 1:
					holder.taskprio.setTextColor(0xFFFF0000);
//					convertView.setBackgroundColor(0xFFFF0000);
					break;
				case 2:
					holder.taskprio.setTextColor(0xFF00FF00);
//					convertView.setBackgroundColor(0xFF00FF00);
					break;
				case 3:
					holder.taskprio.setTextColor(0xFF0000FF);
//					convertView.setBackgroundColor(0xFF0000FF);
					break;
				default:
					holder.taskprio.setTextColor(0xFF555555);
//					convertView.setBackgroundColor(0xFF000000);
				}
			}
			return convertView;
		}

	}
	
	private static class ViewHolder {
		private TextView taskid;
		private TextView taskprio;
		private TextView tasktext;
		private TextView taskcontexts;
	}

}
