package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.Behaviour;

import java.util.*;

public class SpelunkerAgent extends Agent {
    private AID envAID, navAID;
    private Map<String, List<String>> synonyms;

    @Override
    protected void setup() {
        // 1) Знайти EnvironmentAgent
        DFAgentDescription envTpl = new DFAgentDescription();
        ServiceDescription sdEnv = new ServiceDescription();
        sdEnv.setType("WumpusEnvironment");
        envTpl.addServices(sdEnv);
        try {
            DFAgentDescription[] res = DFService.search(this, envTpl);
            if (res.length > 0) envAID = res[0].getName();
        } catch(Exception e) { e.printStackTrace(); }

        // 2) Знайти NavigatorAgent
        DFAgentDescription navTpl = new DFAgentDescription();
        ServiceDescription sdNav = new ServiceDescription();
        sdNav.setType("WumpusNavigator");
        navTpl.addServices(sdNav);
        try {
            DFAgentDescription[] res = DFService.search(this, navTpl);
            if (res.length > 0) navAID = res[0].getName();
        } catch(Exception e) { e.printStackTrace(); }

        // 3) Словник синонімів
        synonyms = new HashMap<>();
        synonyms.put("Breeze", Arrays.asList(
                "I feel a breeze.", "There is a breeze here.", "It's breezy."
        ));
        synonyms.put("Stench", Arrays.asList(
                "I smell a stench.", "There is a foul smell.", "It smells awful."
        ));
        synonyms.put("Glitter", Arrays.asList(
                "I see a glitter.", "There's a sparkling object.", "Something is glinting."
        ));

        synonyms.put("Bump", Arrays.asList(
                "I bumped into something.",
                "There was a bump.",
                "I hit a wall."
        ));
        synonyms.put("Scream", Arrays.asList(
                "I heard a scream.",
                "There was a scream.",
                "I heard it faintly."
        ));


        // 4) Основна поведінка
        addBehaviour(new Behaviour() {
            private int step = 0;
            private boolean finished = false;
            private List<String> lastPercepts = new ArrayList<>();
            private String lastAction = "";

            @Override
            public void action() {
                switch (step) {
                    case 0:
                        // надіслати REQUEST в середовище
                        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                        req.addReceiver(envAID);
                        req.setContent("Describe()");
                        send(req);
                        step = 1;
                        break;

                    case 1:
                        // отримати перцепти
                        ACLMessage resp = receive();
                        if (resp != null) {
                            lastPercepts = parsePercept(resp.getContent());
                            step = 2;
                        } else {
                            block();
                        }
                        break;

                    case 2:
                        // перетворити перцепти в англійську фразу
                        StringBuilder sb = new StringBuilder();
                        Random rnd = new Random();
                        for (String p : lastPercepts) {
                            List<String> opts = synonyms.get(p);
                            if (opts != null && !opts.isEmpty()) {
                                sb.append(opts.get(rnd.nextInt(opts.size())))
                                        .append(" ");
                            }
                        }
                        ACLMessage toNav = new ACLMessage(ACLMessage.INFORM);
                        toNav.addReceiver(navAID);
                        toNav.setContent(sb.toString().trim());
                        send(toNav);
                        step = 3;
                        break;

                    case 3:
                        // отримати від NavigatorAction
                        ACLMessage navResp = receive();
                        if (navResp != null) {
                            lastAction = navResp.getContent().trim();
                            step = 4;
                        } else {
                            block();
                        }
                        break;

                    case 4:
                        // відправити CFP з обраною дією
                        ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                        cfp.addReceiver(envAID);
                        cfp.setContent("Action(" + lastAction + ")");
                        send(cfp);
                        step = 5;
                        break;

                    case 5:
                        // чекати ACCEPT
                        ACLMessage acc = receive();
                        if (acc != null && acc.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                            if ("Climb".equals(lastAction)) {
                                finished = true;
                                doDelete();  // завершуємо агента
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

    /** Розбирає рядок формату "Percepts[Stench, Breeze],3" → ["Stench","Breeze"] */
    private List<String> parsePercept(String content) {
        int b = content.indexOf('[');
        int e = content.indexOf(']');
        if (b < 0 || e < 0 || e <= b + 1) return Collections.emptyList();
        String inside = content.substring(b + 1, e);
        String[] parts = inside.split(",");
        List<String> res = new ArrayList<>();
        for (String p : parts) {
            res.add(p.trim());
        }
        return res;
    }
}
