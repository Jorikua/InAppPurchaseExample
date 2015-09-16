package ua.kaganovych.inapppurchaseexample;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import ua.kaganovych.inapppurchaseexample.util.IabHelper;
import ua.kaganovych.inapppurchaseexample.util.IabResult;
import ua.kaganovych.inapppurchaseexample.util.Inventory;
import ua.kaganovych.inapppurchaseexample.util.Purchase;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private IabHelper mHelper;

    private boolean mIsPremium = false;

    private static final String SKU_MONTH_SUB = "month_sub";
    private static final String SKU_COINS = "coins";

    private static final int PURCHASE_REQUEST = 10001;
    private static final String COINS_COUNT = "coins_count";

    private Button mBuyCoinsButton;
    private Button mBuyPremiumButton;
    private TextView mCoinsCount;
    private RelativeLayout mMainLayout;

    // Current amount of coins in stash
    private int mCoins;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBuyPremiumButton = (Button) findViewById(R.id.buy_premium_button);
        mBuyCoinsButton = (Button) findViewById(R.id.buy_coins_button);
        mCoinsCount = (TextView) findViewById(R.id.coins_text_view);
        mMainLayout = (RelativeLayout) findViewById(R.id.main_layout);

        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(this, Const.PURCHASE_PUBLIC_KEY);

        // enable debug logging (for a production application, you should set this to false).
        mHelper.enableDebugLogging(true);

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");


        mBuyPremiumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Subscription button clicked. Purchasing subscription.");
                String payload = "";
                mHelper.launchSubscriptionPurchaseFlow(
                        MainActivity.this,
                        SKU_MONTH_SUB,
                        PURCHASE_REQUEST,
                        mPurchaseFinishedListener,
                        payload);
            }
        });

        mBuyCoinsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Coins button clicked. Buying one more coin.");
                String payload = "";
                mHelper.launchPurchaseFlow(
                        MainActivity.this,
                        SKU_COINS,
                        PURCHASE_REQUEST,
                        mPurchaseFinishedListener,
                        payload);
            }
        });

        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh noes, there was a problem.
                    Log.d(TAG, "Problem setting up in-app billing: " + result);
                    return;
                }

                // Have we been disposed of in the meantime? If so, quit.
                if (mHelper == null) return;

                // IAB is fully set up. Now, let's get an inventory of stuff we own.
                Log.d(TAG, "Setup successful. Querying inventory.");
                mHelper.queryInventoryAsync(mGotInventoryListener);
            }
        });
    }

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                Log.d(TAG, "Failed to query inventory: " + result);
                return;
            }

            Log.d(TAG, "Query inventory was successful.");

            // Do we have the premium upgrade?
            Purchase premiumPurchase = inventory.getPurchase(SKU_MONTH_SUB);
            mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
            Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));
            if (mIsPremium) {
                updateUi();
            }

            // Check for coins
            Purchase coinsPurchase = inventory.getPurchase(SKU_COINS);
            if (coinsPurchase != null && verifyDeveloperPayload(coinsPurchase)) {
                Log.d(TAG, "We have coins. Consuming it.");
                loadData();
                mHelper.consumeAsync(inventory.getPurchase(SKU_COINS), mConsumeFinishedListener);
                return;
            }
            Log.d(TAG, "Initial inventory query finished; enabling main UI.");
        }
    };

    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.d(TAG, "Error purchasing: " + result);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                Log.d(TAG, "Error purchasing. Authenticity verification failed.");
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(SKU_COINS)) {
                // bought 1 coin. So consume it.
                Log.d(TAG, "Purchase is coin. Starting coin consumption.");
                mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            } else if (purchase.getSku().equals(SKU_MONTH_SUB)) {
                // bought the premium upgrade!
                Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                Toast.makeText(MainActivity.this, "Thank you for upgrading to premium!", Toast.LENGTH_LONG).show();
                mIsPremium = true;
                updateUi();
            }
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            // We know this is the "coins" sku because it's the only one we consume,
            // so we don't check which sku was consumed. If you have more than one
            // sku, you probably should check...
            if (result.isSuccess()) {
                // successfully consumed, so we apply the effects of the item in our
                // game world's logic, which in our case means buying another coin
                Log.d(TAG, "Consumption successful. Provisioning.");
                mCoins = mCoins + 1;
                saveData();
                loadData();
                Toast.makeText(MainActivity.this, "Now you have " + String.valueOf(mCoins), Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "Error while consuming: " + result);
            }
            Log.d(TAG, "End consumption flow.");
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (mHelper == null) return;

        // Pass on the activity result to the helper for handling
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            // not handled, so handle it ourselves (here's where you'd
            // perform any handling of activity results not related to in-app
            // billing...
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.d(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHelper != null) {
            mHelper.dispose();
        }
        mHelper = null;
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    private void updateUi() {
        mBuyPremiumButton.setVisibility(mIsPremium ? View.GONE : View.VISIBLE);
        mMainLayout.setBackgroundColor(mIsPremium ? Color.CYAN : Color.WHITE);
    }

    private void saveData() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(COINS_COUNT, mCoins);
        Log.d(TAG, "Coins saved: " + mCoins);
        editor.commit();
    }

    private void loadData() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mCoins = preferences.getInt(COINS_COUNT, 0);
        mCoinsCount.setText("Number of coins: " + mCoins);
    }
}
