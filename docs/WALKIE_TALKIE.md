# Walkie-talkie

Voice between the people of a group, with two transports behind one coordinator.

```
TransportCoordinator ──► HybridGroupConnectionManager ──┬─► GroupConnectionManager   (Nearby Connections, no internet)
                                                        └─► WebRTCAudioTransport      (internet, any distance)
WalkieTalkieService  (foreground service, keeps the call alive with the screen off)
```

## Transports

- **Nearby Connections** (`GroupConnectionManager`, `AudioSender`, `AudioReceiver`): phones find each other by a service id derived from the group id and stream audio directly. Roughly 100-200 m, no internet needed.
- **WebRTC** (`WebRTCAudioTransport`): voice goes phone to phone, encrypted; Firebase Realtime Database only carries the "who is here" list and the connection set-up messages (`walkie_groups/<group>/users` and `/signaling`).
  - People are identified by account id, and the rules check that a message really comes from the account it names.
  - Only the smaller account id of a pair makes the offer, so two phones never offer at once.
  - Connection candidates that arrive before the offer are queued and applied afterwards.
  - The microphone starts muted; nothing leaves the phone until the person turns it on.
  - A short network drop does not end the call; only a failed connection is closed and re-offered.
- `ConnectionModeManager` watches the network and `HybridGroupConnectionManager` switches between the two.

## Foreground service

`WalkieTalkieService` is started with the call (microphone + connected-device foreground types) and stopped with it. Android would otherwise stop the microphone shortly after the app leaves the screen. The service does no audio work itself.

## Known limits

- STUN only: phones behind strict (symmetric) NATs cannot connect over the internet without a TURN server.
- The "one speaker at a time" lock exists per phone; two people can still talk at once.
- Anyone signed in who knows a group id can join its call (groups are open to join).
- Not yet tested between two real devices.
