package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.Behaviour;

import java.util.*;

public class SpelunkerAgent extends Agent {
    private AID envAID, navAID;
    private Map<String, List<String>> synonyms;

    @Override
    protected void setup() {
        // 1) Знаходимо Environment
        try {
            DFAgentDescription tmpl = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("WumpusEnvironment");
            tmpl.addServices(sd);
            DFAgentDescription[] res = DFService.search(this, tmpl);
            envAID = res[0].getName();
            System.out.println("[Spelunker] Found environment: " + envAID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2) Знаходимо Navigator
        try {
            DFAgentDescription tmpl = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("WumpusNavigator");
            tmpl.addServices(sd);
            DFAgentDescription[] res = DFService.search(this, tmpl);
            navAID = res[0].getName();
            System.out.println("[Spelunker] Found navigator:   " + navAID);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3) Синоніми
        synonyms = new HashMap<>();
        synonyms.put("Breeze", Arrays.asList("I feel a breeze.", "There is a breeze here.", "It's breezy."));
        synonyms.put("Stench", Arrays.asList("I smell a stench.", "There is a foul smell.", "It smells awful."));
        synonyms.put("Glitter", Arrays.asList("I see a glitter.", "There's a sparkling object.", "Something is glinting."));
        synonyms.put("Bump", Arrays.asList("I bumped into something.", "There was a bump.", "I hit a wall."));
        synonyms.put("Scream", Arrays.asList("I heard a scream.", "There was a scream.", "I heard it faintly."));

        // 4) Поведінка-стани
        addBehaviour(new Behaviour() {
            private int step = 0;
            private boolean finished = false;
            private List<String> lastPercepts;
            private String lastAction;

            @Override
            public void action() {
                switch (step) {
                    case 0:
                        System.out.println("[Spelunker] Requesting percept from Env");
                        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                        req.addReceiver(envAID);
                        req.setContent("Describe()");
                        send(req);
                        step = 1;
                        break;

                    case 1:
                        ACLMessage resp = receive();
                        if (resp != null) {
                            System.out.println("[Spelunker] Got from Env: " + resp.getContent());
                            lastPercepts = parsePercept(resp.getContent());
                            step = 2;
                        } else {
                            block();
                        }
                        break;

                    case 2:
                        // Формуємо англійське повідомлення
                        StringBuilder sb = new StringBuilder();
                        Random rnd = new Random();
                        for (String p : lastPercepts) {
                            List<String> opts = synonyms.get(p);
                            if (opts != null && !opts.isEmpty()) {
                                sb.append(opts.get(rnd.nextInt(opts.size()))).append(" ");
                            }
                        }
                        String msgText = sb.toString().trim();
                        if (msgText.isEmpty()) {
                            msgText = "I sense nothing.";
                        }
                        System.out.println("[Spelunker] Sending to Nav: " + msgText);
                        ACLMessage toNav = new ACLMessage(ACLMessage.INFORM);
                        toNav.addReceiver(navAID);
                        toNav.setContent(msgText);
                        send(toNav);
                        step = 3;
                        break;

                    case 3:
                        ACLMessage navResp = receive();
                        if (navResp != null) {
                            lastAction = navResp.getContent().trim();
                            System.out.println("[Spelunker] Got from Nav: " + lastAction);
                            step = 4;
                        } else {
                            block();
                        }
                        break;

                    case 4:
                        System.out.println("[Spelunker] Sending CFP to Env: Action(" + lastAction + ")");
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        cfp.addReceiver(envAID);
                        cfp.setContent("Action(" + lastAction + ")");
                        send(cfp);
                        step = 5;
                        break;

                    case 5:
                        ACLMessage acc = receive();
                        if (acc != null && acc.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                            System.out.println("[Spelunker] Env accepted: " + lastAction);
                            if ("Climb".equals(lastAction)) {
                                System.out.println("[Spelunker] Climbing out, done.");
                                finished = true;
                                doDelete();
                            } else {
                                step = 0;
                            }
                        } else {
                            block();
                        }
                        break;
                }
            }

            @Override
            public boolean done() {
                return finished;
            }
        });
    }

    // Допоміжний метод
    private List<String> parsePercept(String content) {
        int b = content.indexOf('['), e = content.indexOf(']');
        if (b < 0 || e < 0 || e <= b + 1) return Collections.emptyList();
        String[] parts = content.substring(b + 1, e).split(",");
        List<String> res = new ArrayList<>();
        for (String p : parts) res.add(p.trim());
        return res;
    }
}
