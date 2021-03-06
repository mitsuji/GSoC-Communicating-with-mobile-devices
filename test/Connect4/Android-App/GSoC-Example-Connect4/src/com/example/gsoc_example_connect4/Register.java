package com.example.gsoc_example_connect4;

import static com.example.gsoc_example_connect4.CommonUtilities.SENDER_ID;
import static com.example.gsoc_example_connect4.MainActivity.PROPERTY_REG_ID;
import static com.example.gsoc_example_connect4.MainActivity.PROPERTY_ON_SERVER_EXPIRATION_TIME;
import static com.example.gsoc_example_connect4.MainActivity.REGISTRATION_EXPIRY_TIME_MS;

import java.io.IOException;

import com.example.gsoc_example_connect4.R;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;

// Activity to register user and password.
public class Register extends Activity {
	
	private String mUser;
	private String mPassword;
	private View mRegistrationGCMView;
	private View mLoginStatusView;
	private View mLoginFormView;
    private TextView mregistrationGCMMessageView;
	private TextView mLoginStatusMessageView;
	
	// UI references.
	private EditText mUserView;
	private EditText mPasswordView;
	
    GoogleCloudMessaging gcm;
    String regid;
    SharedPreferences prefs;
    
    Context context;
	
    // Creates the initial configuration
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_register);
		
		context = getApplicationContext();
		prefs = getSharedPreferences(MainActivity.class.getSimpleName(),Context.MODE_PRIVATE);
		regid = prefs.getString(PROPERTY_REG_ID, "");

		mLoginFormView = findViewById(R.id.login_form);
		mLoginStatusView = findViewById(R.id.login_status);
		mRegistrationGCMView = findViewById(R.id.registrationGCM_status);
		mUserView = (EditText) findViewById(R.id.user);
		mPasswordView = (EditText) findViewById(R.id.password);
		mregistrationGCMMessageView= (TextView) findViewById(R.id.registrationGCMmessage);
		mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);
		
		if (regid.length() == 0) {
    		registerBackground();
    	}else{
    		askForUserAndPassword();
    	}
		
		gcm = GoogleCloudMessaging.getInstance(this);
	}

	public void askForUserAndPassword() {
		
		mRegistrationGCMView.setVisibility(View.GONE);
		mLoginFormView.setVisibility(View.VISIBLE);
		
		mUserView.setText(mUser);

		mPasswordView
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.login || id == EditorInfo.IME_NULL) {
							attemptLogin();
							return true;
						}
						return false;
					}
				});

		findViewById(R.id.sign_in_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptLogin();
					}
				});
	}
	
	// Intent to register the specified account, if there is any error, (invalid user, field required, etc.)
	// the errors are shown and the registration does not success.
	public void attemptLogin() {

		// Reset errors.
		mUserView.setError(null);
		mPasswordView.setError(null);

		// Save values.
		mUser = mUserView.getText().toString();
		mPassword = mPasswordView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid password.
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		} else if (mPassword.length() < 4) {
			mPasswordView.setError(getString(R.string.error_invalid_password));
			focusView = mPasswordView;
			cancel = true;
		}

		// Check for a valid user.
		if (TextUtils.isEmpty(mUser)) {
			mUserView.setError(getString(R.string.error_field_required));
			focusView = mUserView;
			cancel = true;
		} else if (mUser.length() < 4) {
			mUserView.setError(getString(R.string.error_invalid_user));
			focusView = mUserView;
			cancel = true;
		}
				
		if (cancel) {
			// There is an error, so registration does not success and focus on the error.
			focusView.requestFocus();
		} else {
			// Send information to server.
			mLoginStatusMessageView.setText(R.string.login_progress_registering);
			showProgress(true);
			sendInfoToServer();
		}
	}
	
	// Shows the progress UI and hides the login form.
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}
	
	// Registers the application with GCM servers asynchronously.
    // Stores the registration id, and expiration time in the 
    // application's shared preferences.
    private void registerBackground() {
        new AsyncTask<Void,Void,Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    mregistrationGCMMessageView.setText(R.string.looking_for_regid);
                    regid = gcm.register(SENDER_ID);
                    
                    // Save the regid - no need to register again.
                    setRegistrationId(context, regid);

                } catch (IOException ex) {
                	Log.v("RegisterGCM", "Registration not found. " + ex);
                	return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if(!result)
                	mregistrationGCMMessageView.setText(R.string.failed_regid);
                else
                	askForUserAndPassword();
            }
        }.execute(null, null, null);
    }
    
    private void setRegistrationId(Context context, String regId) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);

        long expirationTime = System.currentTimeMillis() + REGISTRATION_EXPIRY_TIME_MS;
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }
    
    // Send the information to the server asynchronously.
    // Save the success of failure in the variable "registered".
    private void sendInfoToServer(){
    	new AsyncTask<Void, Void, Boolean>() {

    		@Override
    		protected Boolean doInBackground(Void... parameters) {
    			return ServerUtilities.register(context,regid,mUser,mPassword);
    		}
    		protected void onPostExecute(Boolean result) {
    			if(result){//Sends result to MainActivity.
    				showProgress(false);
    				SharedPreferences.Editor editor = prefs.edit();
             	    editor.putBoolean("registered", result);
             	    editor.putString("user", mUser);
       			    editor.putString("password", mPassword);
       			    editor.commit();
            	    Intent i = new Intent();
        			i.putExtra("USER",mUser);
        			i.putExtra("PASSWORD",mPassword);
        			i.putExtra("REGID",regid);
        			setResult(RESULT_OK,i);
        			finish();
    			}
    			else{
    				showProgress(false);
    				AlertDialog dialog;
    				AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setMessage(R.string.couldnt_register)
                           .setTitle(R.string.couldnt_register_title)
                    	   .setNeutralButton(R.string.close, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            ;
                        }
                    });
                    dialog = builder.create();
                    dialog.show();
    			}
    	    }
    		protected void onCancelled() {
    			showProgress(false);
    		}
    	}.execute(null,null,null);
    }

}
