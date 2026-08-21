"View-Refactoring"
Current state view after registration, we have an "menu"
    - Device information
    - Qr-Read
    - Connected devices
    - Desktop sessions
    - Namespaces
and an
    - Notification "Box"
and an    
- Accept/Decline button


The refactoring should be like:
    - Adding an "settings icon" in the same row as the "Home" text
    - Click on it
        => show button "Backup - device settings" (this is the current view under "device information"). We have to keep the same functionality
        => show button "Connected devices" => the view is currently under the "Namespaces" => we have to move the view under this button keeping the same functionality but we have to show and organized on this way:
        (try to visualize as connections-relations)
            Device Name 
              |
               -> namespace 
               -> namespace
            we show only the following informations in one namespace "box"
                > Namespace url
                > "Gateway" => rename for "as Backup connection device"
            we do not have to show anymore, we still have to keep all the information as before:
                > namespaceId, accountId, deviceId
            keep to functionality as it was before ( for example delete )

        It means, that we do not need on the main view menu anymore:
        - namespaces button
        - connected devices button => we do not need the "view behind" anymore, becaues the "content" will be
        under settings => "Connected devices" visualized

        => Button on the main view "Desktop sessions" we keep the button, but we need an new functionality
            currently we see the devices with "live" sessions if we click on the button
            => Changes: the app should check every 15 seconds the "sessions", and if we have any "live" session,
            we should show on the "main screen" the "Desktop" name with a sign "live session". For clicking this notification the user should be able to "revoke session", using the same function, which we already have under the "Desktop sessions" view


        

