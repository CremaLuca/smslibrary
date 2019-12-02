package com.eis.smslibrary;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eis.communication.CommunicationHandler;
import com.eis.smslibrary.listeners.SMSReceivedServiceListener;
import com.eis.smslibrary.listeners.SMSSentListener;

import java.lang.ref.WeakReference;

import it.lucacrema.preferences.PreferencesManager;


/**
 * Communication handler for SMSs. It's a Singleton, you should
 * access it with {@link #getInstance}, and before doing anything you
 * should call {@link #setup}.<br/>
 *
 * @author Luca Crema, Marco Mariotto, Alberto Ursino, Marco Tommasini
 * @since 29/11/2019
 */
public class SMSHandler implements CommunicationHandler<SMSMessage> {

    public static final String SENT_MESSAGE_INTENT_ACTION = "SMS_SENT";
    public static final int RANDOM_STARTING_COUNTER_VALUE_RANGE = 100000;

    /**
     * Singleton instance
     */
    private static SMSHandler instance;

    /**
     * Weak reference doesn't prevent garbage collector to
     * de-allocate this class when it has reference to a
     * context that is still running. Prevents memory leaks.
     */
    private WeakReference<Context> context;

    /**
     * This message counter is used so that we can have a different action name
     * for pending intent (that will call broadcastReceiver). If we were to use the
     * same action name for every message we would have a conflict and we wouldn't
     * know what message has been sent
     */
    private int messageCounter;

    /**
     * Private constructor for Singleton
     */
    private SMSHandler() {
        //Random because if we close and open the app the value probably differs
        messageCounter = (int) (Math.random() * RANDOM_STARTING_COUNTER_VALUE_RANGE);
    }

    /**
     * @return the current instance of this class
     */
    public static SMSHandler getInstance() {
        if (instance == null)
            instance = new SMSHandler();
        return instance;
    }

    /**
     * Setup for the handler.
     *
     * @param context current context.
     */
    public void setup(Context context) {
        this.context = new WeakReference<>(context);
    }

    /**
     * Sends a message to a destination peer via SMS.
     * Requires {@link android.Manifest.permission#SEND_SMS}
     *
     * @param message to be sent in the channel to a peer
     */
    @Override
    public void sendMessage(final @NonNull SMSMessage message) {
        sendMessage(message, null);
    }

    /**
     * Sends a message to a destination peer via SMS then
     * calls the listener.
     *
     * @param message      to be sent in the channel to a peer
     * @param sentListener called on message sent or on error, can be null
     */
    public void sendMessage(final @NonNull SMSMessage message, final @Nullable SMSSentListener sentListener) {
        checkSetup();
        String smsContent = SMSMessageHandler.getInstance().parseData(message);
        PendingIntent sentPI = setupNewSentReceiver(message, sentListener);

        SMSCore.sendMessage(smsContent, message.getPeer().getAddress(), sentPI, null);
    }

    /**
     * Creates a new {@link SMSSentBroadcastReceiver} and registers it to receive broadcasts
     * with action {@value SENT_MESSAGE_INTENT_ACTION}
     *
     * @param message  that will be sent
     * @param listener to call on broadcast received
     * @return a {@link PendingIntent} to be passed to SMSCore
     */
    private PendingIntent setupNewSentReceiver(final @NonNull SMSMessage message, final @Nullable SMSSentListener listener) {
        if (listener == null)
            return null; //Doesn't make any sense to have a BroadcastReceiver if there is no listener

        SMSSentBroadcastReceiver onSentReceiver = new SMSSentBroadcastReceiver(message, listener);
        String actionName = SENT_MESSAGE_INTENT_ACTION + (messageCounter++);
        context.get().registerReceiver(onSentReceiver, new IntentFilter(actionName));
        return PendingIntent.getBroadcast(context.get(), 0, new Intent(actionName), 0);
    }

    /**
     * Checks if the handler has been setup
     *
     * @throws IllegalStateException if the handler has not been setup
     */
    private void checkSetup() {
        if (context == null)
            throw new IllegalStateException("You must call setup() first");
    }

    /**
     * Saves in memory the service class name to wake up. It doesn't need an
     * instance of the class, it just saves the name and instantiates it when needed.
     *
     * @param receivedListenerClassName the listener called on message received
     * @param <T>                       the class type that extends {@link SMSReceivedServiceListener} to be called
     */
    public <T extends SMSReceivedServiceListener> void setReceivedListener(Class<T> receivedListenerClassName) {
        checkSetup();
        PreferencesManager.setString(context.get(), SMSReceivedBroadcastReceiver.SERVICE_CLASS_PREFERENCES_KEY, receivedListenerClassName.toString());
    }

    /**
     * Unsubscribe the current {@link SMSReceivedServiceListener} from being called on message arrival
     */
    public void removeReceivedListener(){
        checkSetup();
        PreferencesManager.removeValue(context.get(), SMSReceivedBroadcastReceiver.SERVICE_CLASS_PREFERENCES_KEY);
    }

}
