# Offline name-based gender suggestions

The model is shared by Android (assets/name-gender-model.json) and Windows/Chrome (desktop/assets/name-gender-model.json). Regenerate both with node scripts/train-name-gender-model.cjs.

It uses a curated reference vocabulary of 485 Roman-script example names and a character 2-4-gram naive Bayes classifier. These example labels were authored for the feature, not taken from verified member records. No LLM, hosted API, network lookup, photo, CNIC, guardian name or social data is used. Names are processed only on the local device.

The runtime removes titles and common prefixes, uses the first meaningful given-name token, abstains on an explicit list of ambiguous names and family-name-only input, and uses a coverage/margin threshold for unfamiliar names. Internal scores are not calibrated probabilities and are not displayed as confidence percentages. Unknown and unsupported-script names may have no suggestion. Outputs do not establish a person's gender identity or official demographic record.

The model is a small initial baseline. Test cases establish runtime behavior and correction preservation; they do not establish real-world accuracy. Evaluation on independently confirmed local data is needed before making any accuracy claim.

Suggestions are shown only when there is no saved gender. The user must apply a suggestion or choose a different value, then press Submit. Saved gender values are never overwritten by the classifier. Choosing Male, Female, Other or Not provided remains available. The suggestion is separate from saved data; Gender insight counts change only after a value is saved. Source imports and SQLite refreshes prioritize saved nonempty gender values. An empty saved value can still show a suggestion, but it is not automatically filled.

General background (not the source of training labels or model weights):
- https://thesai.org/Publications/ViewPaper?Code=IJACSA&Issue=5&SerialNo=25&Volume=7
- https://aclanthology.org/W15-5921/
