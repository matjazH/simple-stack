package com.zhuinden.simplestackdemoexamplemvp.application;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;

import com.squareup.coordinators.Coordinator;
import com.squareup.coordinators.CoordinatorProvider;
import com.squareup.coordinators.Coordinators;
import com.transitionseverywhere.TransitionManager;
import com.zhuinden.simplestack.Backstack;
import com.zhuinden.simplestack.BackstackDelegate;
import com.zhuinden.simplestack.HistoryBuilder;
import com.zhuinden.simplestack.StateChange;
import com.zhuinden.simplestack.StateChanger;
import com.zhuinden.simplestackdemoexamplemvp.R;
import com.zhuinden.simplestackdemoexamplemvp.data.manager.DatabaseManager;
import com.zhuinden.simplestackdemoexamplemvp.presentation.paths.first.FirstKey;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity
        extends AppCompatActivity
        implements StateChanger {
    @BindView(R.id.drawer_layout)
    MainView mainView;

    @BindView(R.id.root)
    RelativeLayout root;

    public interface OptionsItemSelectedListener {
        boolean onOptionsItemSelected(MenuItem menuItem);
    }

    public static final String TAG = "MainActivity";

    public static MainActivity get(Context context) {
        // noinspection ResourceType
        return (MainActivity)context.getSystemService(TAG);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mainView.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mainView.onCreateOptionsMenu(menu);
    }

    BackstackDelegate backstackDelegate;

    @Inject
    DatabaseManager databaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mainView.onCreate();

        CustomApplication.get(this).getComponent().inject(this);
        databaseManager.init(this);

        MainScopeListener mainScopeListener = (MainScopeListener)getSupportFragmentManager().findFragmentByTag("MAIN_SCOPE_LISTENER");
        if(mainScopeListener == null) {
            mainScopeListener = new MainScopeListener();
            getSupportFragmentManager().beginTransaction().add(mainScopeListener, "MAIN_SCOPE_LISTENER").commit();
        }
        CustomApplication.get(this).getComponent().inject(mainScopeListener);

        Coordinators.installBinder(root, new CoordinatorProvider() {
            @Nullable
            @Override
            public Coordinator provideCoordinator(View view) {
                Log.i(TAG, "Providing coordinator for [" + view + "]");
                Key key = Backstack.getKey(view.getContext());
                return key.newCoordinator(); // maybe should be obtained from a component
            }
        });
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        backstackDelegate = new BackstackDelegate(this);
        backstackDelegate.onCreate(savedInstanceState, //
                getLastCustomNonConfigurationInstance(), //
                HistoryBuilder.single(FirstKey.create()));
        mainView.onPostCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mainView.onConfigChanged(newConfig);
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return backstackDelegate.onRetainCustomNonConfigurationInstance();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        backstackDelegate.onPostResume();
    }

    @Override
    public void onBackPressed() {
        if(mainView.onBackPressed()) {
            return;
        }
        if(!backstackDelegate.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        backstackDelegate.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        backstackDelegate.persistViewToState(root.getChildAt(0));
        backstackDelegate.onSaveInstanceState(outState);
    }

    @Override
    public Object getSystemService(String name) {
        if(TAG.equals(name)) {
            return this;
        }
        if(backstackDelegate != null && backstackDelegate.isSystemService(name)) {
            return backstackDelegate.getSystemService(name);
        }
        return super.getSystemService(name);
    }

    @Override
    public void handleStateChange(StateChange stateChange, Callback completionCallback) {
        if(stateChange.topNewState().equals(stateChange.topPreviousState())) {
            // no-op
            completionCallback.stateChangeComplete();
            return;
        }
        mainView.handleStateChange(stateChange, () -> {
        });
        TransitionManager.beginDelayedTransition(root);
        Log.i(TAG, "Persisting view state of [" + root.getChildAt(0) + "]");
        backstackDelegate.persistViewToState(root.getChildAt(0));
        root.removeAllViews();
        Key newKey = stateChange.topNewState();
        Context newContext = stateChange.createContext(this, newKey);
        View view = LayoutInflater.from(newContext).inflate(newKey.layout(), root, false);
        Log.i(TAG, "Adding view [" + view + "]");
        root.addView(view);
        Log.i(TAG, "Restoring view state of [" + view + "]");
        backstackDelegate.restoreViewFromState(view);
        backstackDelegate.clearStatesNotIn(stateChange.getNewState());

        mainView.setupViewsForKey(newKey);
        completionCallback.stateChangeComplete();
    }
}
