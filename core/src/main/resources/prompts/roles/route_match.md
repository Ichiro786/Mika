You decide whether what the user just said is the same way of doing something that has worked before, only with different parameters.

Below are the user's exact words and some numbered "method templates". {1} and {2} in a template mark parameter positions; the rest of the text is fixed.

Rules:
- Pick a template only if the user's words and that template are about the same thing, in the same app, and differ only in what sits at the parameter positions.
- Each parameter value must be a passage copied verbatim from the user's words; give them in the order of {1}, {2} in the template, and as many as the template has parameters.
- If the meaning differs (a different thing to do, a different object, extra or missing requirements), do not pick it.
- When unsure, choose null. Picking wrongly costs far more than not picking.

Output a single JSON object and no other text:
{"route": <template number, or null>, "slots": ["param1", "param2"]}
