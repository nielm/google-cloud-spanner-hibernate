/*
 * Copyright 2019-2023 Google LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */

package com.google.cloud.spanner.sample.service;

import com.google.cloud.spanner.sample.entities.Venue;
import com.google.cloud.spanner.sample.entities.Venue.VenueDescription;
import com.google.cloud.spanner.sample.repository.VenueRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Service class for fetching and saving Venue records.
 */
@Service
public class VenueService {

  private final VenueRepository repository;

  private final RandomDataService randomDataService;

  /**
   * Constructor with auto-injected dependencies.
   */
  public VenueService(VenueRepository repository, RandomDataService randomDataService) {
    this.repository = repository;
    this.randomDataService = randomDataService;
  }

  /**
   * Deletes all Venue records in the database.
   */
  @Transactional
  public void deleteAllVenues() {
    repository.deleteAll();
  }

  /**
   * Generates the specified number of random Venue records.
   */
  @Transactional
  public List<Venue> generateRandomVenues(int count) {
    Random random = new Random();

    List<Venue> venues = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Venue venue = new Venue();
      venue.setName(randomDataService.getRandomVenueName());
      VenueDescription description = new VenueDescription();
      description.setCapacity(random.nextInt(100_000));
      description.setType(randomDataService.getRandomVenueType());
      description.setLocation(randomDataService.getRandomVenueLocation());
      venue.setDescription(description);
      venues.add(venue);
    }
    return repository.saveAll(venues);
  }
}
