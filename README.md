# LogRhythm
Work in progress; Android app to search call logs for phone numbers and to launch a call or text intent.  Users not from America should modify hashSetContainsNumber() accordingly.  We aggressively search for duplicates in this function by leveraging the '+' char and the length of the country code, so you may wish to simply look for exact matches instead.

# Motivation
I have not found a phone app that lets users search for parts of a number in your call log if they are not in your contacts.  Many times I have to return a call and have to manually look through my call log to find a number that, for example, has "778" somewhere in the middle.

# Disclaimer
This software is distributed AS IS with no warranty expressed or implied.  It the end user's sole responsibility to read and understand the code in this project before executing it on any machine.  I hereby release myself from any and all liabilities!
