package org.a5calls.android.a5calls.controller;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.FiveCallsApplication;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.model.FiveCallsApi;
import org.a5calls.android.a5calls.model.Issue;

import java.util.Collections;
import java.util.List;

/**
 * The activity which handles zip code lookup and showing the issues list.
 *
 * TODO: Add loading spinners when making Volley requests.
 * TODO: Add error message if the device is offline?
 * TODO: Add full "personal stats" DialogFragment that shows lots of information.
 * TODO: Add an email address sign-up field.
 * TODO: Sort issues based on which are "done" and which are not done or hide ones which are "done".
 * TODO: After making a call, jump the screen up to show the next contact!
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final String PREFS_FILE = "fiveCallsPrefs";
    public static final String KEY_INITIALIZED = "prefsKeyInitialized";
    public static final String KEY_ALLOW_ANALYTICS = "prefsKeyAllowAnalytics";
    public static final String KEY_USER_ZIP = "prefsKeyUserZip";
    public static final String KEY_LATITUDE = "prefsKeyLatitude";
    public static final String KEY_LONGITUDE = "prefsKeyLongitude";
    private static final int ISSUE_DETAIL_REQUEST = 1;

    private IssuesAdapter mIssuesAdapter;
    private FiveCallsApi.RequestStatusListener mStatusListener;
    private String mZip;
    private String mLatitude;
    private String mLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Consider using fragments
        super.onCreate(savedInstanceState);

        // See if we've had this user before. If not, start them at tutorial type page.
        // TODO: This may have been a mistake to do this in PREFS_FILE, should probably copy over
        // to defaultSharedPreferences. Then SettingsFragment can be more simple.
        SharedPreferences pref = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);
        boolean initialized = pref.getBoolean(KEY_INITIALIZED, false);
        if (!initialized) {
            Intent intent = new Intent(this, TutorialActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Load the location the user last used, if any.
        String code = pref.getString(KEY_USER_ZIP, "");
        String longitude = pref.getString(KEY_LONGITUDE, "");
        if (TextUtils.isEmpty(code) && TextUtils.isEmpty(longitude)) {
            // No location set, go to LocationActivity!
            Intent intent = new Intent(this, LocationActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        RecyclerView issuesRecyclerView = (RecyclerView) findViewById(R.id.issues_recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        issuesRecyclerView.setLayoutManager(layoutManager);
        mIssuesAdapter = new IssuesAdapter();
        issuesRecyclerView.setAdapter(mIssuesAdapter);

        mStatusListener = new FiveCallsApi.RequestStatusListener() {
            @Override
            public void onRequestError() {
                Snackbar.make(findViewById(R.id.activity_main),
                        getResources().getString(R.string.request_error),
                        Snackbar.LENGTH_INDEFINITE).show();
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setIssues(Collections.<Issue>emptyList());
            }

            @Override
            public void onJsonError() {
                Snackbar.make(findViewById(R.id.activity_main),
                        getResources().getString(R.string.json_error),
                        Snackbar.LENGTH_LONG).show();
                // Our only type of request in MainActivity is a GET. If it doesn't work, clear the
                // active issues list to avoid showing a stale list.
                mIssuesAdapter.setIssues(Collections.<Issue>emptyList());
            }

            @Override
            public void onIssuesReceived(String locationName, List<Issue> issues) {
                getSupportActionBar().setTitle(String.format(getResources().getString(
                        R.string.title_main), locationName));
                mIssuesAdapter.setIssues(issues);
            }

            @Override
            public void onCallCount(int count) {
                // unused
            }

            @Override
            public void onCallReported() {
                // unused
            }
        };
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .registerStatusListener(mStatusListener);
    }

    @Override
    protected void onDestroy() {
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .unregisterStatusListener(mStatusListener);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences pref = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);
        mZip = pref.getString(KEY_USER_ZIP, "");
        mLongitude = pref.getString(KEY_LONGITUDE, "");
        mLatitude = pref.getString(KEY_LATITUDE, "");
        onLocationUpdated();

        // We allow Analytics opt-out.
        if (pref.getBoolean(KEY_ALLOW_ANALYTICS, true)) {
            // Obtain the shared Tracker instance.
            FiveCallsApplication application = (FiveCallsApplication) getApplication();
            Tracker tracker = application.getDefaultTracker();
            tracker.setScreenName(TAG);
            tracker.send(new HitBuilders.ScreenViewBuilder().build());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_stats) {
            showStats();
            return true;
        } else if (item.getItemId() == R.id.menu_refresh) {
            onLocationUpdated();
            return true;
        } else if (item.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (item.getItemId() == R.id.menu_location) {
            Intent intent = new Intent(this, LocationActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showStats() {
        // TODO: Show the stats in a DialogFragment or even an activity.
        int callsCount = AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper()
                .getCallsCount();
        String message = String.format(getResources().getString(R.string.stats_message),
                callsCount);
        Snackbar.make(findViewById(R.id.activity_main), message, Snackbar.LENGTH_LONG).show();
    }

    private void onLocationUpdated() {
        if (TextUtils.isEmpty(mLongitude)) {
            onZipUpdated(mZip);
        } else {
            onLatLongUpdated(mLatitude, mLongitude);
        }
    }

    private void onZipUpdated(String zip) {
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .getIssuesForLocation(zip);
    }

    private void onLatLongUpdated(String latitude, String longitude) {
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .getIssuesForLocation(latitude + "," + longitude);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ISSUE_DETAIL_REQUEST && resultCode == RESULT_OK) {
            Issue issue = data.getExtras().getParcelable(IssueActivity.KEY_ISSUE);
            mIssuesAdapter.updateIssue(issue);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class IssuesAdapter extends RecyclerView.Adapter<IssueViewHolder> {
        private List<Issue> mIssues = Collections.emptyList();

        public IssuesAdapter() {

        }

        public void setIssues(List<Issue> issues) {
            mIssues = issues;
            notifyDataSetChanged();
        }

        public void updateIssue(Issue issue) {
            for (int i = 0; i < mIssues.size(); i++) {
                if (TextUtils.equals(issue.id, mIssues.get(i).id)) {
                    mIssues.set(i, issue);
                    notifyItemChanged(i);
                    return;
                }
            }
        }

        @Override
        public IssueViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            CardView v = (CardView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.issue_view, parent, false);
            IssueViewHolder vh = new IssueViewHolder(v);
            return vh;
        }

        @Override
        public void onBindViewHolder(final IssueViewHolder holder, int position) {
            final Issue issue = mIssues.get(position);
            holder.name.setText(issue.name);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent issueIntent = new Intent(holder.itemView.getContext(),
                            IssueActivity.class);
                    issueIntent.putExtra(IssueActivity.KEY_ISSUE, issue);
                    issueIntent.putExtra(IssueActivity.KEY_ZIP, mZip);
                    startActivityForResult(issueIntent, ISSUE_DETAIL_REQUEST);
                }
            });
            int totalCalls = issue.contacts.length;
            List<String> contacted = AppSingleton.getInstance(getApplicationContext())
                    .getDatabaseHelper().getCallsForIssueAndZip(issue.id, mZip);
            int callsLeft = totalCalls - contacted.size();
            if (callsLeft == totalCalls) {
                if (totalCalls == 1) {
                    holder.numCalls.setText(getResources().getString(R.string.call_count_one));
                } else {
                    holder.numCalls.setText(String.format(
                            getResources().getString(R.string.call_count), totalCalls));
                }
            } else {
                if (callsLeft == 1) {
                    holder.numCalls.setText(String.format(
                            getResources().getString(R.string.call_count_remaining_one),
                            totalCalls));
                } else {
                    holder.numCalls.setText(String.format(
                            getResources().getString(R.string.call_count_remaining), callsLeft,
                            totalCalls));
                }
            }
            holder.doneIcon.setImageLevel(callsLeft == 0 ? 1 : 0);
        }

        @Override
        public void onViewRecycled(IssueViewHolder holder) {
            holder.itemView.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return mIssues.size();
        }
    }

    private class IssueViewHolder extends RecyclerView.ViewHolder {
        public TextView name;
        public TextView numCalls;
        public ImageView doneIcon;

        public IssueViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.issue_name);
            numCalls = (TextView) itemView.findViewById(R.id.issue_call_count);
            doneIcon = (ImageView) itemView.findViewById(R.id.issue_done_img);
        }
    }
}
