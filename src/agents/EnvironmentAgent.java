package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import java.util.*;

public class EnvironmentAgent extends Agent {
    private final int size = 4;
    private boolean[][] pits;
    private boolean[][] wumpus;
    private boolean[][] gold;
    private int agentX, agentY;
    private String agentDir;
    private int timeTick;

    @Override
    protected void setup() {
        // 1) Ініціалізація поля
        pits   = new boolean[size][size];
        wumpus = new boolean[size][size];
        gold   = new boolean[size][size];
        // Приклад розстановки — ви можете змінити/доповнити
        pits[1][3]    = true;
        pits[2][1]    = true;
        wumpus[2][2]  = true;
        gold[0][2]    = true;

        agentX   = 0;
        agentY   = 0;
        agentDir = "EAST";
        timeTick = 0;

        // 2) Реєстрація в DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("WumpusEnvironment");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3) Поведінка: обробляємо REQUEST і CFP
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:
                            // відповідаємо перцептами
                            addBehaviour(new OneShotBehaviour() {
                                @Override
                                public void action() {
                                    String p = buildPercepts();           // "[Stench, Breeze, ...]"
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.INFORM);
                                    reply.setContent("Percepts" + p + "," + timeTick);
                                    timeTick++;
                                    send(reply);
                                }
                            });
                            break;
                        case ACLMessage.CFP:
                            // застосовуємо дію і повертаємо ACCEPT
                            String content = msg.getContent();  // "Action(Forward)"
                            addBehaviour(new OneShotBehaviour() {
                                @Override
                                public void action() {
                                    String act = parseAction(content);
                                    applyAction(act);
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                    reply.setContent("OK");
                                    send(reply);
                                }
                            });
                            break;
                        default:
                            break;
                    }
                } else {
                    block();
                }
            }
        });
    }

    // Збираємо список перцептів у поточній клітинці
    private String buildPercepts() {
        List<String> p = new ArrayList<>();
        if (wumpus[agentX][agentY]) p.add("Stench");
        if (pits[agentX][agentY])   p.add("Breeze");
        if (gold[agentX][agentY])   p.add("Glitter");
        // TODO: додати Bump/Scream за потреби
        return p.toString();
    }

    // Розбираємо "Action(Forward)" → "Forward"
    private String parseAction(String content) {
        if (content.startsWith("Action(") && content.endsWith(")")) {
            return content.substring(7, content.length() - 1);
        }
        return content;
    }

    // Застосування дії
    private void applyAction(String action) {
        switch (action) {
            case "Forward":   moveForward(); break;
            case "TurnLeft":  turnLeft();    break;
            case "TurnRight": turnRight();   break;
            case "Grab":      grabGold();    break;
            case "Shoot":     shoot();       break;
            case "Climb":     doDelete();    break;  // вихід
            default: break;
        }
    }

    private void moveForward() {
        int nx = agentX, ny = agentY;
        switch (agentDir) {
            case "EAST":  nx++; break;
            case "WEST":  nx--; break;
            case "NORTH": ny++; break;
            case "SOUTH": ny--; break;
        }
        if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
            agentX = nx; agentY = ny;
        }
        // інакше — Bump, але тут не обробляємо
    }

    private void turnLeft() {
        agentDir = switch (agentDir) {
            case "EAST"  -> "NORTH";
            case "NORTH" -> "WEST";
            case "WEST"  -> "SOUTH";
            case "SOUTH" -> "EAST";
            default      -> agentDir;
        };
    }

    private void turnRight() {
        agentDir = switch (agentDir) {
            case "EAST"  -> "SOUTH";
            case "SOUTH" -> "WEST";
            case "WEST"  -> "NORTH";
            case "NORTH" -> "EAST";
            default      -> agentDir;
        };
    }

    private void grabGold() {
        if (gold[agentX][agentY]) {
            gold[agentX][agentY] = false;
        }
    }

    private void shoot() {
        int tx = agentX, ty = agentY;
        switch (agentDir) {
            case "EAST":  tx++; break;
            case "WEST":  tx--; break;
            case "NORTH": ty++; break;
            case "SOUTH": ty--; break;
        }
        if (tx >= 0 && tx < size && ty >= 0 && ty < size) {
            wumpus[tx][ty] = false;
        }
    }
}
