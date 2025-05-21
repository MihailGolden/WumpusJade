package agents;

import aima.core.agent.Percept;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import aima.core.environment.wumpusworld.AgentPercept;
import aima.core.environment.wumpusworld.HybridWumpusAgent;
import aima.core.agent.Action;

import java.util.*;

public class NavigatorAgent extends Agent {
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
        } catch(Exception e) {
            e.printStackTrace();
        }

        // 2) Поведінка: слухаємо SpelunkerAgent
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    addBehaviour(new OneShotBehaviour() {
                        @Override
                        public void action() {
                            // парсимо англомовне повідомлення з перцептами
                            Set<AgentPercept> percepts = extractPercepts(msg.getContent());
                            // запускаємо AIMA-агента
                            HybridWumpusAgent aimaAgent = new HybridWumpusAgent();
                            Action act = aimaAgent.execute((Percept) percepts);
                            // відправляємо назад просте ім’я дії
                            ACLMessage reply = msg.createReply();
                            reply.setPerformative(ACLMessage.INFORM);
                            reply.setContent(act.toString());
                            send(reply);
                        }
                    });
                } else {
                    block();
                }
            }
        });
    }

    /** Збираємо jeden AgentPercept за текстом (кожен унікальний набір ознак) */
    private Set<AgentPercept> extractPercepts(String text) {
        String t = text.toLowerCase();
        boolean s = t.contains("stench");
        boolean b = t.contains("breeze");
        boolean g = t.contains("glitter");
        boolean bump   = t.contains("bump");
        boolean scream = t.contains("scream");
        AgentPercept percept = new AgentPercept(s, b, g, bump, scream);
        return Collections.singleton(percept);
    }
}
