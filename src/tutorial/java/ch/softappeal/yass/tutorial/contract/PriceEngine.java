package ch.softappeal.yass.tutorial.contract;

import java.util.List;

public interface PriceEngine {

  void subscribe(List<String> instrumentIds) throws UnknownInstrumentsException;

}
