package org.unifiedpush.android.connector

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import kotlin.collections.ArrayList

object UnifiedPush {

    const val FEATURE_BYTES_MESSAGE = "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"
    @JvmStatic
    val DEFAULT_FEATURES = arrayListOf(FEATURE_BYTES_MESSAGE)

    @JvmStatic
    fun registerApp(
        context: Context,
        instance: String = INSTANCE_DEFAULT,
        features: ArrayList<String> = DEFAULT_FEATURES,
        messageForDistributor: String = ""
    ) {
        val store = Store(context)
        val token = store.getTokenOrNew(instance)

        val distributor = getSavedDistributor(context) ?: return

        val broadcastIntent = Intent()
        broadcastIntent.`package` = distributor
        broadcastIntent.action = ACTION_REGISTER
        broadcastIntent.putExtra(EXTRA_TOKEN, token)
        broadcastIntent.putExtra(EXTRA_FEATURES, features)
        broadcastIntent.putExtra(EXTRA_MESSAGE, messageForDistributor)
        broadcastIntent.putExtra(EXTRA_APPLICATION, context.packageName)
        context.sendBroadcast(broadcastIntent)
    }

    @JvmStatic
    @Deprecated(
        "Replace with registerAppWithDialog",
        replaceWith = ReplaceWith("registerAppWithDialog(" +
            "context, instance, RegistrationDialogContent().apply { noDistributorDialog.message = dialogMessage }, features, messageForDistributor" +
                    ")")
    )
    fun registerAppWithDialog(
        context: Context,
        instance: String = INSTANCE_DEFAULT,
        dialogMessage: String,
        features: ArrayList<String> = DEFAULT_FEATURES,
        messageForDistributor: String = ""
    ) {
        registerAppWithDialog(
            context,
            instance,
            RegistrationDialogContent().apply { noDistributorDialog.message = dialogMessage },
            features,
            messageForDistributor
        )
    }

    @JvmStatic
    fun registerAppWithDialog(
        context: Context,
        instance: String = INSTANCE_DEFAULT,
        registrationDialogContent: RegistrationDialogContent =
            RegistrationDialogContent(),
        features: ArrayList<String> = DEFAULT_FEATURES,
        messageForDistributor: String = ""
    ) {
        getAckDistributor(context)?.let {
            registerApp(context, instance)
            return
        }

        val distributors = getDistributors(context, features)

        when (distributors.size) {
            0 -> {
                if (!Store(context).getNoDistributorAck()) {
                    val message = TextView(context)
                    val builder = AlertDialog.Builder(context)
                    val s = SpannableString(registrationDialogContent.noDistributorDialog.message)
                    Linkify.addLinks(s, Linkify.WEB_URLS)
                    message.text = s
                    message.movementMethod = LinkMovementMethod.getInstance()
                    message.setPadding(32, 32, 32, 32)
                    builder.setTitle(registrationDialogContent.noDistributorDialog.title)
                    builder.setView(message)
                    builder.setPositiveButton(registrationDialogContent.noDistributorDialog.okButton) {
                            _, _ ->
                    }
                    builder.setNegativeButton(registrationDialogContent.noDistributorDialog.ignoreButton) {
                            _, _ ->
                        Store(context).saveNoDistributorAck()
                    }
                    builder.show()
                } else {
                    Log.d(LOG_TAG, "User already know there isn't any distributor")
                }
            }
            1 -> {
                saveDistributor(context, distributors.first())
                registerApp(context, instance, features, messageForDistributor)
            }
            else -> {
                val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                builder.setTitle(registrationDialogContent.chooseDialog.title)

                val distributorsArray = distributors.toTypedArray()
                val distributorsNameArray = distributorsArray.map {
                    try {
                        val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getApplicationInfo(
                                it,
                                PackageManager.ApplicationInfoFlags.of(
                                    PackageManager.GET_META_DATA.toLong()
                                )
                            )
                        } else {
                            context.packageManager.getApplicationInfo(it, 0)
                        }
                        context.packageManager.getApplicationLabel(ai)
                    } catch (e: PackageManager.NameNotFoundException) {
                        it
                    } as String
                }.toTypedArray()
                builder.setItems(distributorsNameArray) { _, which ->
                    val distributor = distributorsArray[which]
                    saveDistributor(context, distributor)
                    Log.d(LOG_TAG, "saving: $distributor")
                    registerApp(context, instance, features, messageForDistributor)
                }
                val dialog: AlertDialog = builder.create()
                dialog.show()
            }
        }
    }

    @JvmStatic
    fun removeNoDistributorDialogACK(context: Context) {
        Store(context).removeNoDistributorAck()
    }

    @JvmStatic
    fun unregisterApp(context: Context, instance: String = INSTANCE_DEFAULT) {
        val store = Store(context)
        val distributor = getSavedDistributor(context) ?: run {
            store.removeInstances()
            store.removeDistributor()
            return
        }
        val token = store.tryGetToken(instance) ?: return
        val broadcastIntent = Intent()
        broadcastIntent.`package` = distributor
        broadcastIntent.action = ACTION_UNREGISTER
        broadcastIntent.putExtra(EXTRA_TOKEN, token)
        store.removeInstance(instance, removeDistributor = true)
        context.sendBroadcast(broadcastIntent)
    }

    @JvmStatic
    fun getDistributors(
        context: Context,
        features: ArrayList<String> = DEFAULT_FEATURES
    ): List<String> {
        return (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryBroadcastReceivers(
                    Intent(ACTION_REGISTER),
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.GET_META_DATA.toLong() +
                            PackageManager.GET_RESOLVED_FILTER.toLong()
                    )
                )
            } else {
                context.packageManager.queryBroadcastReceivers(
                    Intent(ACTION_REGISTER),
                    PackageManager.GET_RESOLVED_FILTER
                )
            }
            ).mapNotNull {
            val packageName = it.activityInfo.packageName

            features.forEach { feature ->
                it.filter?.let { filter ->
                    if (!filter.hasAction(feature)) {
                        Log.i(
                            LOG_TAG,
                            "Found distributor $packageName" +
                                " without feature $feature"
                        )
                        return@mapNotNull null
                    }
                } ?: run {
                    Log.w(LOG_TAG, "Cannot filter distributors with features")
                }
            }
            if (it.activityInfo.exported || packageName == context.packageName) {
                Log.d(LOG_TAG, "Found distributor with package name $packageName")
                packageName
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun saveDistributor(context: Context, distributor: String) {
        Store(context).saveDistributor(distributor)
    }

    @JvmStatic
    @Deprecated(
        "Replace with getSavedDistributor or getAckDistributor",
        replaceWith = ReplaceWith("getAckDistributor(context)")
    )
    fun getDistributor(context: Context): String  = getAckDistributor(context) ?: ""

    /**
     * This function returns the distributor registered by the user,
     * but the distributor may not have sent a new endpoint yet
     */
    @JvmStatic
    fun getSavedDistributor(context: Context): String? = getDistributor(context, false)

    /**
     * This function returns the distributor registered by the user,
     * and the distributor has already sent a new endpoint
     */
    @JvmStatic
    fun getAckDistributor(context: Context): String? = getDistributor(context, true)

    @JvmStatic
    private fun getDistributor(context: Context, ack: Boolean): String? {
        val store = Store(context)
        if (ack && !store.distributorAck) {
            return null
        }
        return store.tryGetDistributor()?.let { distributor ->
            if (distributor in getDistributors(context)) {
                Log.d(LOG_TAG, "Found saved distributor.")
                distributor
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun safeRemoveDistributor(context: Context) {
        val store = Store(context)
        if (store.getInstances().isEmpty()) {
            store.removeDistributor()
        }
    }

    @JvmStatic
    fun forceRemoveDistributor(context: Context) {
        val store = Store(context)
        store.getInstances().forEach {
            store.removeInstance(it)
        }
        store.removeInstances()
        store.removeDistributor()
    }
}
