## PhraseSaver

Saves json-dictionary (see [wiktionary-utils](https://github.com/vanzay/wiktionary-utils)) to vocabulario DB

1. Run phrase saver
2. Make manual fixes if needed
3. Run queries to update *group_number* field
```
UPDATE phrase SET group_number = phrase_id
WHERE language_id = <language id> AND base_phrase_id is null
```
```
UPDATE phrase SET group_number = get_group_number(phrase_id)
WHERE language_id = <language id> AND group_number = 0
```

## IndexBuilder

Builds phrase indexes based on vocabulario DB
