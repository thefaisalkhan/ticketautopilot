const SAMPLE_TICKETS = [
  {
    subject: "Overcharged on my last invoice — refund ASAP",
    body: "I was charged twice for my subscription this month. This is urgent, please refund the duplicate charge immediately, it's critical.",
  },
  {
    subject: "Question about invoice line items",
    body: "Can you explain what the platform fee on my invoice covers? No rush, whenever you have time.",
  },
  {
    subject: "Payment failed, card declined",
    body: "My credit card payment failed today and now my account shows past due. This is affecting my team's access, please help soon, it's important.",
  },
  {
    subject: "How do I update my billing address?",
    body: "How do I change the billing address on my invoice? Not urgent, just want to update it before the next billing cycle.",
  },
  {
    subject: "Billing thing",
    body: "Hey, something's off with my bill I think? Not sure, can someone check?",
  },
  {
    subject: "App crashes immediately on login — production down",
    body: "Since this morning the app crashes as soon as I try to log in. This is blocking my entire team and needs urgent attention, it's critical.",
  },
  {
    subject: "Minor UI glitch, not urgent",
    body: "There's a small visual glitch on the settings page, whenever you get a chance to look at it, no rush at all.",
  },
  {
    subject: "Data not syncing across devices",
    body: "My data isn't syncing correctly between my phone and the web app, it's not working properly. This is affecting several customers on our team.",
  },
  {
    subject: "Getting a 500 error when exporting reports",
    body: "Every time I try to export my monthly report, I get a 500 error. I need it fixed soon, it's important for my team.",
  },
  {
    subject: "App keeps crashing when I open attachments",
    body: "Every time I try to open an email attachment the app keeps crashing. Nothing critical, but it's happening constantly and it's annoying.",
  },
  {
    subject: "Please add dark mode",
    body: "It would be great if you could add dark mode support. This is affecting many users on our team, it's important to us.",
  },
  {
    subject: "Feature request: CSV export for reports",
    body: "Can you add a way to export reports as CSV? Would love to see this in a future update, no rush at all.",
  },
  {
    subject: "Would it be possible to add webhook support?",
    body: "It would be nice to have webhook notifications when a ticket status changes. Just a suggestion for a future release.",
  },
  {
    subject: "Mobile app when?",
    body: "Any plans for a mobile app? This would be a great enhancement and honestly kind of urgent for our field team since they're stuck using the website on their phones.",
  },
  {
    subject: "How do I export my data to a spreadsheet?",
    body: "How to export all my data as a CSV file? Documentation would help, no rush.",
  },
  {
    subject: "Where can I find my API key?",
    body: "Where is the API key setting located? I checked the docs but couldn't find it, some guide would help. It's somewhat important since I need it today.",
  },
  {
    subject: "Not sure how to invite a teammate",
    body: "Not sure how to add a new team member to my workspace. Could really use a guide or tutorial, this is kind of high priority for us since we're onboarding this week.",
  },
  {
    subject: "Where is the setting to change my timezone?",
    body: "Where can I update my timezone in the account settings? Just curious, not a big deal.",
  },
  {
    subject: "Instructions for setting up SSO",
    body: "Do you have documentation or a guide for configuring SSO with our identity provider? This is blocking our enterprise rollout and quite urgent.",
  },
  {
    subject: "How do I reset my password?",
    body: "How to reset my password, I forgot my password and cannot log in. No rush, whenever you get a chance.",
  },
  {
    subject: "Locked out of my account",
    body: "I'm locked out of my account after too many failed login attempts. This is urgent, I need access immediately for a client call.",
  },
  {
    subject: "Can't verify my email address",
    body: "I never received the verification email and can't verify my email address to activate my account. Not urgent, whenever is fine.",
  },
  {
    subject: "Forgot my username",
    body: "I forgot my username and the login page won't accept my email either. Fairly high priority since I have a client demo in an hour.",
  },
  {
    subject: "2FA codes not arriving",
    body: "I'm not receiving my two-factor authentication codes via SMS and can't log in. This is blocking me from an important deadline today.",
  },
  {
    subject: "Question about my account",
    body: "Hey, something seems off, can someone check?",
  },
  {
    subject: "It's not working",
    body: "Not sure what's going on but it just doesn't seem right. Can you take a look?",
  },
  {
    subject: "Random question",
    body: "Not really sure who to ask about this but wanted to check in on something.",
  },
];
