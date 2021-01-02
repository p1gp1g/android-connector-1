# UP-lib
![Release](https://jitpack.io/v/UnifiedPush/UP-lib.svg)

This is a library that can be used by an end user application to receive notifications from any unified push provider.
An [example application](https://github.com/UnifiedPush/UP-example) is available to show basic usage of the library.

## Index

* [Install Library](#install-library)
* [Register For Push](#register-for-push)
* [Receiving Push Messages](#receiving-push-messages)
* [Sending Push Messages](#sending-push-messages) (from the application server)
* [Using the FCM-added version](#using-the-fcm-added-version)


## Install Library

We are currently using jitpack to distributre the library. Add the following two code snippeds to you corresponding
build files to include the library in your project.

Add the jitpack repo to the **project level** build.gradle:
```gradle
allprojects {
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add the dependency to the **app** build.gradle. Replace {VERSION} with the release you wish to use
```gradle
dependencies {
    // ...
    implementation 'com.github.UnifiedPush:UP-lib:{VERSION}'
}
```

## Register for Push

To register for receiving push services you have two options:

1. Have the library handle distributor selection
```kotlin
// Call the library function
registerAppWithDialog(context)
```

2. Handle selection yourself
```kotlin
// Get a list of distributors that are available
val distributors = getDistributors(context)
// select one or show a dialog or whatever ^^
// the below line will crash the app if no distributors are available
saveDistributor(context, distributors[0])
registerApp(context)
```

**unregister**
```kotlin
// inform the library that you would like to unregister from receiving push messages
unregisterApp(context)
```

## Receiving Push Messages

To receive Push Messages you should extend the class _MessagingReceiver_ and implement the three methods
```kotlin
val handler = object: MessagingReceiverHandler{
    override fun onMessage(context: Context?, message: String) {
        // Called when a new message is received. The String contains the full POST body of the push message
    }

    override fun onNewEndpoint(context: Context?, endpoint: String) {
        // Called when a new endpoint be used for sending push messages
    }

    override fun onUnregistered(context: Context?){
        // called when this application is unregistered from receiving push messages
    }
}

class CustomReceiver: MessagingReceiver(handler)
```

## Sending Push Messages
(From the application server)

To send a message to an application you need the "endpoint". You get it in the onNewEndpoint method once it is available. You can then use it to send a message using for example curl. The POST body is the message received by the function onMessage.
```bash
curl -X POST "$endpoint" --data "Any message body that is desired."
```

## Using the FCM-added version

* Migrate the imported functions from `org.unifiedpush.android.connector` to `org.unifiedpush.android.connector_fcm_added`.
* Add `classpath 'com.google.gms:google-services:4.3.4'` to you project level build.gradle.
* Add `id 'com.google.gms.google-services'` to your app level build.gradle.
* Add the google-services.json file from firebase to your app directory.
* Add the actions `org.unifiedpush.android.distributor.REGISTER` and `org.unifiedpush.android.distributor.UNREGISTER` to your receiver on the manifest.
* Change the receiver handler from `MessagingReceiverHandler` to `MessagingReceiverHandlerFCM` and add the getEndpoint function.
* Change the receiver class from `MessagingReceiver` to `MessagingReceiverFCM`.

For instance, [here](https://github.com/UnifiedPush/UP-example/commit/2aad6fd18e7d03437fe586e08a869d22b1e0069d) is the commit doing the migration from the main version to the fcm-added version on the example application.
