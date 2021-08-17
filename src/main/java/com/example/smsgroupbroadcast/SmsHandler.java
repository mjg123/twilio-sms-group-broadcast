package com.example.smsgroupbroadcast;

import com.twilio.twiml.MessagingResponse;
import com.twilio.twiml.messaging.Body;
import com.twilio.twiml.messaging.Message;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
public class SmsHandler {

    private final Set<String> groupPhoneNumbers;

    public SmsHandler() {
        groupPhoneNumbers = Set.of(System.getenv("GROUP_PHONE_NUMBERS").split(","));
    }

    @RequestMapping(
        value = "/sms",
        method = {RequestMethod.GET, RequestMethod.POST},
        produces = "application/xml")
    @ResponseBody
    public String handleSmsWebhook(@RequestParam("From") String fromNumber,
                                   @RequestParam("To") String twilioNumber,
                                   @RequestParam("Body") String messageBody) {

        List<Message> outgoingMessages;

        if (groupPhoneNumbers.contains(fromNumber)) {
            outgoingMessages = messagesSentFromGroup(fromNumber, twilioNumber, messageBody);

        } else {
            outgoingMessages = messagesSentToGroup(fromNumber, twilioNumber, messageBody);
        }

        MessagingResponse.Builder responseBuilder = new MessagingResponse.Builder();
        outgoingMessages.forEach(responseBuilder::message);
        return responseBuilder.build().toXml();
    }

    private List<Message> messagesSentToGroup(String fromNumber, String twilioNumber, String messageBody) {

        List<Message> messages = new ArrayList<>();

        String finalMessage = "From " + fromNumber + " " + messageBody;
        groupPhoneNumbers.forEach(groupMemberNumber ->
            messages.add(createMessageTwiml(groupMemberNumber, twilioNumber, finalMessage))
        );

        return messages;
    }

    private Message createMessageTwiml(String to, String from, String body) {
        return new Message.Builder()
            .to(to)
            .from(from)
            .body(new Body.Builder(body).build())
            .build();
    }

    private List<Message> messagesSentFromGroup(String fromNumber, String twilioNumber, String messageBody) {
        List<Message> messages = new ArrayList<>();

        String[] messageParts = messageBody.split("\\s+", 2);

        String e164Regex = "\\+[0-9]+";
        if (messageParts.length != 2 || !messageParts[0].matches(e164Regex)) {
            return List.of(createHowToMessage(fromNumber, twilioNumber));
        }

        String realToNumber = messageParts[0];
        String realMessageBody = messageParts[1];

        // add the message to the non-group recipient
        messages.add(
            createMessageTwiml(realToNumber, twilioNumber, realMessageBody)
        );

        // send a copy of the message to everyone in the group except the sender
        String groupCopyMessage = "To " + realToNumber + " " + realMessageBody;
        groupPhoneNumbers.forEach(groupMemberNumber -> {
            if (!groupMemberNumber.equals(fromNumber)) {
                messages.add(
                    createMessageTwiml(groupMemberNumber, twilioNumber, groupCopyMessage));
            }
        });

        return messages;
    }

    private Message createHowToMessage(String fromNumber, String twilioNumber) {
        return createMessageTwiml(fromNumber, twilioNumber,
            "To send a message to someone outside your group, " +
            "don't forget to include the destination phone number at the start, " +
            "eg '+44xxxx Ahoy!'");
    }
}
