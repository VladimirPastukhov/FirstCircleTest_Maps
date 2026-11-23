Implementation of [test task](https://firstcircle.notion.site/Senior-Engineer-Test-1971a58904488098a5d9db72b4d76619).

## Decisions

- No DB, even in memory DB as H2, service saves all data in collections (that how I understood the task)
- No currency, just abstract money
- No account ids, just names
- Added idempotence key aka `opId` to money methods, because it is common approach in fintech
- AI usage: grill-me skill + openSpec.
- I've tried in-memory DB approach too: https://github.com/VladimirPastukhov/FirstCircleTest_H2, but decided that is not what task is asking about
