package agents;

import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import aima.core.environment.wumpusworld.AgentPercept;
import aima.core.environment.wumpusworld.HybridWumpusAgent;
// *** тут правильний імпорт інтерфейсу Action ***
import aima.core.agent.Action;

public class NavigatorAgent extends Agent {
    private HybridWumpusAgent aimaAgent;

    @Override
    protected void setup() {
        // 1) Реєстрація в DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("WumpusNavigator");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2) Ініціалізація одного екземпляра AIMA-агента
        aimaAgent = new HybridWumpusAgent();

        // 3) Поведінка: слухаємо повідомлення від SpelunkerAgent
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    addBehaviour(new OneShotBehaviour() {
                        @Override
                        public void action() {
                            String text = msg.getContent().trim();
                            System.out.println("[Navigator] Received: " + text);

                            // 4) Розбір перцептів
                            String lower = text.toLowerCase();
                            boolean stench  = lower.contains("stench");
                            boolean breeze  = lower.contains("breeze");
                            boolean glitter = lower.contains("glitter");
                            boolean bump    = lower.contains("bump");
                            boolean scream  = lower.contains("scream");

                            // 5) Будуємо AgentPercept
                            AgentPercept percept = new AgentPercept(
                                    stench, breeze, glitter, bump, scream
                            );
                            System.out.println("[Navigator] Parsed percept → " + percept);

                            // 6) Викликаємо AIMA-агента
                            Action act = aimaAgent.execute(percept);

                            // 7) Беремо чисту назву дії через toString()
                            String actionName = act.toString();
                            if(actionName.contains("[")){
                                actionName = actionName.substring(actionName.indexOf("==")+2, actionName.indexOf(","));
                            }
                            System.out.println("[Navigator] Suggesting: " + actionName);

                            // 8) Відправляємо назад SpelunkerAgent
                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent(actionName);
                            send(reply);
                        }
                    });
                } else {
                    block();
                }
            }
        });
    }
}
